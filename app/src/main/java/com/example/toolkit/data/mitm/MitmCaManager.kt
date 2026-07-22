package com.example.toolkit.data.mitm

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Generates and persists the NEXUS root CA used to MITM HTTPS.
 *
 * On Android the name "BC" is a **stripped** platform BouncyCastle that does
 * NOT implement SHA256withRSA — never call setProvider("BC") for signing.
 *
 * CA subject MUST include Organization (O=…) — Android's install UI shows
 * "certificate from null" when O is missing.
 */
class MitmCaManager(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "mitm_ca").also { it.mkdirs() }
    private val caCertFile = File(dir, "nexus-mitm-ca.crt")
    private val caKeyFile = File(dir, "nexus-mitm-ca.key.pk8")
    private val caVersionFile = File(dir, "ca_version")
    private val hostCache = ConcurrentHashMap<String, HostMaterial>()

    val caCertificate: X509Certificate
    private val caKeyPair: KeyPair

    init {
        val loaded = runCatching {
            if (caCertFile.exists() && caKeyFile.exists() && isCurrentCa(caCertFile)) {
                val cert = loadCert(caCertFile)
                val key = loadPrivateKey(caKeyFile)
                HostMaterial(cert, KeyPair(cert.publicKey, key))
            } else null
        }.onFailure {
            Log.w(TAG, "CA load failed, regenerating", it)
            wipeCaFiles()
        }.getOrNull()

        val material = loaded ?: generateCa().also { fresh ->
            wipeCaFiles()
            caCertFile.writeBytes(fresh.cert.encoded)
            caKeyFile.writeBytes(fresh.keyPair.private.encoded)
            caVersionFile.writeText(CA_VERSION)
            Log.i(TAG, "Generated CA subject=${fresh.cert.subjectX500Principal.name}")
        }
        caCertificate = material.cert
        caKeyPair = material.keyPair
    }

    fun caPem(): String {
        val b64 = android.util.Base64.encodeToString(caCertificate.encoded, android.util.Base64.DEFAULT)
            .trim()
            .chunked(64)
            .joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----\n"
    }

    fun caDerFile(): File = caCertFile

    /** DER bytes for KeyChain / Settings install. */
    fun caDerBytes(): ByteArray = caCertificate.encoded

    fun organizationName(): String =
        caCertificate.subjectX500Principal.name
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("O=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: "NEXUS Toolkit"

    fun serverSslContext(host: String): SSLContext {
        val material = hostCache.getOrPut(host.lowercase()) { mintHost(host) }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setKeyEntry("mitm", material.keyPair.private, PASS, arrayOf(material.cert, caCertificate))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, PASS)
        return SSLContext.getInstance("TLS").also { it.init(kmf.keyManagers, null, SecureRandom()) }
    }

    private fun isCurrentCa(certFile: File): Boolean {
        if (caVersionFile.readTextOrEmpty() != CA_VERSION) return false
        val cert = runCatching { loadCert(certFile) }.getOrNull() ?: return false
        val dn = cert.subjectX500Principal.name
        // Old builds had missing/empty O → Android UI shows "from null"
        if (!dn.contains("O=", ignoreCase = true)) return false
        if (dn.contains("O=null", ignoreCase = true)) return false
        return true
    }

    private fun wipeCaFiles() {
        runCatching { caCertFile.delete() }
        runCatching { caKeyFile.delete() }
        runCatching { caVersionFile.delete() }
        hostCache.clear()
    }

    private fun mintHost(host: String): HostMaterial {
        val kp = rsaKeyPair()
        val now = System.currentTimeMillis()
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, host)
            .build()
        val issuer = X500Name.getInstance(caCertificate.subjectX500Principal.encoded)
        val serial = BigInteger(64, SecureRandom())
        val builder = JcaX509v3CertificateBuilder(
            issuer, serial,
            Date(now - 86_400_000L),
            Date(now + 825L * 86_400_000L),
            subject, kp.public
        )
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.extendedKeyUsage, false,
            org.bouncycastle.asn1.x509.ExtendedKeyUsage(
                org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_serverAuth
            )
        )
        builder.addExtension(
            Extension.subjectAlternativeName, false,
            GeneralNames(GeneralName(GeneralName.dNSName, host))
        )
        val extUtils = JcaX509ExtensionUtils()
        builder.addExtension(
            Extension.subjectKeyIdentifier, false,
            extUtils.createSubjectKeyIdentifier(kp.public)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier, false,
            extUtils.createAuthorityKeyIdentifier(caCertificate)
        )
        val holder = builder.build(contentSigner(caKeyPair.private))
        return HostMaterial(toX509(holder), kp)
    }

    private fun generateCa(): HostMaterial {
        val kp = rsaKeyPair()
        val now = System.currentTimeMillis()
        // Explicit RDNs — Android CertInstaller reads O= for the trust warning text.
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.C, "US")
            .addRDN(BCStyle.O, "NEXUS Toolkit")
            .addRDN(BCStyle.OU, "Authorized Testing")
            .addRDN(BCStyle.CN, "NEXUS MITM CA")
            .build()
        val serial = BigInteger(64, SecureRandom())
        val builder = JcaX509v3CertificateBuilder(
            subject, serial,
            Date(now - 86_400_000L),
            Date(now + 3650L * 86_400_000L),
            subject, kp.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(/* isCA */ true))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature)
        )
        val extUtils = JcaX509ExtensionUtils()
        builder.addExtension(
            Extension.subjectKeyIdentifier, false,
            extUtils.createSubjectKeyIdentifier(kp.public)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier, false,
            extUtils.createAuthorityKeyIdentifier(kp.public)
        )
        val holder = builder.build(contentSigner(kp.private))
        return HostMaterial(toX509(holder), kp)
    }

    /**
     * Sign with the **platform** crypto provider. Never pass provider "BC" —
     * Android's built-in BC stub does not implement SHA256withRSA.
     */
    private fun contentSigner(key: PrivateKey): ContentSigner {
        val errors = mutableListOf<String>()
        val attempts = listOf(
            { JcaContentSignerBuilder("SHA256withRSA").setProvider("AndroidOpenSSL").build(key) },
            { JcaContentSignerBuilder("SHA256withRSA").setProvider("Conscrypt").build(key) },
            { JcaContentSignerBuilder("SHA256withRSA").build(key) },
            { JcaContentSignerBuilder("SHA256WithRSA").build(key) }
        )
        for (attempt in attempts) {
            try {
                return attempt()
            } catch (e: Exception) {
                errors += e.message ?: e.javaClass.simpleName
            }
        }
        throw IllegalStateException(
            "Cannot create SHA256withRSA signer on this device: ${errors.joinToString(" | ")}"
        )
    }

    private fun toX509(holder: X509CertificateHolder): X509Certificate {
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun rsaKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        return kpg.generateKeyPair()
    }

    private fun loadCert(file: File): X509Certificate {
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        return file.inputStream().use { cf.generateCertificate(it) as X509Certificate }
    }

    private fun loadPrivateKey(file: File): PrivateKey {
        val bytes = file.readBytes()
        val spec = java.security.spec.PKCS8EncodedKeySpec(bytes)
        return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun File.readTextOrEmpty(): String =
        runCatching { if (exists()) readText().trim() else "" }.getOrDefault("")

    private data class HostMaterial(val cert: X509Certificate, val keyPair: KeyPair)

    companion object {
        private const val TAG = "MitmCaManager"
        /** Bump to force regenerate CAs missing Organization / SKI. */
        private const val CA_VERSION = "4"
        private val PASS = "nexus".toCharArray()
    }
}
