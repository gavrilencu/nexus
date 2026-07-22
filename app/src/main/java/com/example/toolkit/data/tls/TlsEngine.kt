package com.example.toolkit.data.tls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class TlsProtocol(val name: String, val supported: Boolean, val weak: Boolean)

data class TlsCert(
    val subject: String,
    val issuer: String,
    val san: List<String>,
    val validFrom: String,
    val validUntil: String,
    val expired: Boolean,
    val selfSigned: Boolean,
    val sigAlg: String,
    val keyInfo: String,
    val sha256: String
)

data class TlsResult(
    val host: String,
    val port: Int,
    val negotiatedProtocol: String? = null,
    val negotiatedCipher: String? = null,
    val cipherWeak: Boolean = false,
    val protocols: List<TlsProtocol> = emptyList(),
    val chain: List<TlsCert> = emptyList(),
    val hostnameMatches: Boolean? = null,
    val issues: List<String> = emptyList(),
    val grade: String = "?",
    val error: String? = null
)

/**
 * Native TLS configuration analyzer. Opens raw [SSLSocket]s to test which
 * protocol versions the server negotiates, reads the presented certificate
 * chain, and flags weak protocols/ciphers, expiry, self-signing and hostname
 * mismatches — then rolls it all into a letter grade.
 */
class TlsEngine {

    private val weakProtocols = setOf("SSLv3", "TLSv1", "TLSv1.1")
    private val weakCipherMarkers = listOf("RC4", "_DES_", "3DES", "NULL", "EXPORT", "MD5", "ANON")

    suspend fun analyze(rawHost: String): TlsResult = withContext(Dispatchers.IO) {
        val (host, port) = parse(rawHost)
        if (host.isBlank()) return@withContext TlsResult(host, port, error = "Invalid host")

        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val candidateProtocols = listOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
        val supportedByDevice = runCatching {
            (factory.createSocket() as SSLSocket).supportedProtocols.toSet()
        }.getOrDefault(emptySet())

        val protocolResults = ArrayList<TlsProtocol>()
        var negotiatedProtocol: String? = null
        var negotiatedCipher: String? = null
        var chain: List<TlsCert> = emptyList()
        var hostnameMatches: Boolean? = null

        for (proto in candidateProtocols) {
            if (proto !in supportedByDevice) {
                protocolResults.add(TlsProtocol(proto, supported = false, weak = proto in weakProtocols))
                continue
            }
            val ok = runCatching {
                factory.createSocket(host, port).use { raw ->
                    val s = raw as SSLSocket
                    s.enabledProtocols = arrayOf(proto)
                    s.soTimeout = 7000
                    s.startHandshake()
                    val session = s.session
                    if (negotiatedCipher == null) {
                        negotiatedProtocol = session.protocol
                        negotiatedCipher = session.cipherSuite
                        val certs = session.peerCertificates.filterIsInstance<X509Certificate>()
                        chain = certs.map { it.toInfo() }
                        hostnameMatches = certs.firstOrNull()?.let { matchesHost(it, host) }
                    }
                    true
                }
            }.getOrDefault(false)
            protocolResults.add(TlsProtocol(proto, supported = ok, weak = proto in weakProtocols))
        }

        if (chain.isEmpty() && negotiatedCipher == null) {
            return@withContext TlsResult(host, port, protocols = protocolResults,
                error = "No TLS handshake succeeded (host unreachable or not TLS).")
        }

        val cipherWeak = negotiatedCipher?.let { c -> weakCipherMarkers.any { c.uppercase().contains(it) } } ?: false
        val issues = buildIssues(protocolResults, negotiatedCipher, cipherWeak, chain, hostnameMatches)
        TlsResult(
            host = host, port = port,
            negotiatedProtocol = negotiatedProtocol, negotiatedCipher = negotiatedCipher,
            cipherWeak = cipherWeak, protocols = protocolResults, chain = chain,
            hostnameMatches = hostnameMatches, issues = issues, grade = grade(issues, protocolResults)
        )
    }

    private fun buildIssues(
        protocols: List<TlsProtocol>, cipher: String?, cipherWeak: Boolean,
        chain: List<TlsCert>, hostMatch: Boolean?
    ): List<String> = buildList {
        protocols.filter { it.supported && it.weak }.forEach {
            add("Weak protocol enabled: ${it.name}")
        }
        if (protocols.none { it.supported && it.name == "TLSv1.2" } && protocols.none { it.supported && it.name == "TLSv1.3" })
            add("No modern TLS (1.2/1.3) negotiated")
        if (cipherWeak) add("Weak cipher suite negotiated: $cipher")
        chain.firstOrNull()?.let { leaf ->
            if (leaf.expired) add("Leaf certificate is EXPIRED (until ${leaf.validUntil})")
            if (leaf.selfSigned) add("Leaf certificate is self-signed")
            if (leaf.sigAlg.uppercase().contains("SHA1")) add("Weak signature algorithm: ${leaf.sigAlg}")
        }
        if (hostMatch == false) add("Certificate hostname does NOT match")
        if (isEmpty()) add("No obvious TLS misconfiguration detected")
    }

    private fun grade(issues: List<String>, protocols: List<TlsProtocol>): String {
        val hasModern = protocols.any { it.supported && (it.name == "TLSv1.3") }
        val criticals = issues.count {
            it.contains("EXPIRED") || it.contains("Weak cipher") || it.contains("hostname does NOT") ||
                it.contains("self-signed") || it.contains("No modern TLS")
        }
        val weakProto = issues.count { it.startsWith("Weak protocol") }
        return when {
            criticals > 0 -> "F"
            weakProto >= 2 -> "C"
            weakProto == 1 -> "B"
            hasModern -> "A+"
            else -> "A"
        }
    }

    private fun X509Certificate.toInfo(): TlsCert {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val san = runCatching {
            subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() } ?: emptyList()
        }.getOrDefault(emptyList())
        val pk = publicKey
        val bits = when (pk) {
            is java.security.interfaces.RSAPublicKey -> pk.modulus.bitLength()
            is java.security.interfaces.ECPublicKey -> pk.params.order.bitLength()
            else -> null
        }
        return TlsCert(
            subject = subjectDN.name, issuer = issuerDN.name, san = san,
            validFrom = fmt.format(notBefore), validUntil = fmt.format(notAfter),
            expired = notAfter.before(Date()),
            selfSigned = subjectDN == issuerDN,
            sigAlg = sigAlgName,
            keyInfo = pk.algorithm + (bits?.let { " $it-bit" } ?: ""),
            sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(encoded)
                .joinToString("") { "%02x".format(it) }
        )
    }

    private fun matchesHost(cert: X509Certificate, host: String): Boolean {
        val names = runCatching {
            cert.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() } ?: emptyList()
        }.getOrDefault(emptyList()).toMutableList()
        val cn = Regex("CN=([^,]+)").find(cert.subjectDN.name)?.groupValues?.get(1)
        if (cn != null) names.add(cn)
        return names.any { hostMatchesPattern(host, it.trim()) }
    }

    private fun hostMatchesPattern(host: String, pattern: String): Boolean {
        if (pattern.startsWith("*.")) {
            val suffix = pattern.substring(1)
            return host.endsWith(suffix) && host.removeSuffix(suffix).count { it == '.' } == 0
        }
        return host.equals(pattern, ignoreCase = true)
    }

    private fun parse(raw: String): Pair<String, Int> {
        var s = raw.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        var port = 443
        if (s.contains(":")) {
            port = s.substringAfter(":").toIntOrNull() ?: 443
            s = s.substringBefore(":")
        }
        return s to port
    }
}
