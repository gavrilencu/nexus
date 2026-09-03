package com.example.toolkit.data.hibp

import android.content.Context
import com.example.toolkit.data.network.HttpClients
import com.example.toolkit.security.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class HibpBreach(
    val name: String,
    val title: String,
    val domain: String,
    val breachDate: String,
    val addedDate: String,
    val pwnCount: Long,
    val description: String,
    val dataClasses: List<String>,
    val isVerified: Boolean,
    val isSensitive: Boolean,
    val isRetired: Boolean,
    val isSpamList: Boolean,
    val logoPath: String?
)

data class HibpPaste(
    val source: String,
    val id: String,
    val title: String?,
    val date: String?,
    val emailCount: Int
)

data class HibpEmailResult(
    val account: String,
    val breached: Boolean,
    val breaches: List<HibpBreach>,
    val pastes: List<HibpPaste>,
    val error: String? = null,
    val needsApiKey: Boolean = false
)

data class HibpPasswordResult(
    val pwned: Boolean,
    val count: Long,
    val prefix: String,
    val error: String? = null
)

class HibpEngine(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("nexus_hibp", Context.MODE_PRIVATE)
    private val userAgent = "NEXUS-Toolkit-Android"

    fun getApiKey(): String {
        prefs.getString(KEY_ENC, null)?.let { enc ->
            return runCatching { KeystoreCipher.decryptString(HIBP_KEY_ALIAS, enc) }.getOrDefault("")
        }
        // Migrate a key saved in plaintext by an older build, then drop the plaintext.
        val legacy = prefs.getString(KEY_LEGACY, "").orEmpty()
        if (legacy.isNotBlank()) {
            saveApiKey(legacy)
            return legacy
        }
        return ""
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        val editor = prefs.edit().remove(KEY_LEGACY)
        if (trimmed.isBlank()) {
            editor.remove(KEY_ENC)
        } else {
            editor.putString(KEY_ENC, KeystoreCipher.encryptString(HIBP_KEY_ALIAS, trimmed))
        }
        editor.apply()
    }

    suspend fun checkEmail(account: String): HibpEmailResult = withContext(Dispatchers.IO) {
        val email = account.trim()
        if (email.isBlank() || !email.contains("@")) {
            return@withContext HibpEmailResult(
                account = account,
                breached = false,
                breaches = emptyList(),
                pastes = emptyList(),
                error = "Enter a valid email address"
            )
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext HibpEmailResult(
                account = email,
                breached = false,
                breaches = emptyList(),
                pastes = emptyList(),
                needsApiKey = true,
                error = "HIBP API key required. Get one at haveibeenpwned.com/API/Key (personal keys available)."
            )
        }

        try {
            val encoded = URLEncoder.encode(email, StandardCharsets.UTF_8.name())
            val url = "https://haveibeenpwned.com/api/v3/breachedaccount/$encoded"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("truncateResponse", "false")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("hibp-api-key", apiKey)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

            HttpClients.default.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        val breaches = parseBreaches(JSONArray(body))
                        val pastes = fetchPastes(email, apiKey)
                        HibpEmailResult(
                            account = email,
                            breached = breaches.isNotEmpty(),
                            breaches = breaches.sortedByDescending { it.breachDate },
                            pastes = pastes
                        )
                    }
                    404 -> HibpEmailResult(
                        account = email,
                        breached = false,
                        breaches = emptyList(),
                        pastes = emptyList()
                    )
                    401 -> HibpEmailResult(
                        account = email,
                        breached = false,
                        breaches = emptyList(),
                        pastes = emptyList(),
                        needsApiKey = true,
                        error = "Invalid API key (401). Check your key at haveibeenpwned.com/API/Key"
                    )
                    429 -> HibpEmailResult(
                        account = email,
                        breached = false,
                        breaches = emptyList(),
                        pastes = emptyList(),
                        error = "Rate limited (429). Wait a moment and retry."
                    )
                    403 -> HibpEmailResult(
                        account = email,
                        breached = false,
                        breaches = emptyList(),
                        pastes = emptyList(),
                        error = "Forbidden (403). Check User-Agent / API access."
                    )
                    else -> HibpEmailResult(
                        account = email,
                        breached = false,
                        breaches = emptyList(),
                        pastes = emptyList(),
                        error = "HIBP HTTP ${response.code}: ${response.body?.string()?.take(200)}"
                    )
                }
            }
        } catch (e: Exception) {
            HibpEmailResult(
                account = email,
                breached = false,
                breaches = emptyList(),
                pastes = emptyList(),
                error = e.message
            )
        }
    }

    private fun fetchPastes(email: String, apiKey: String): List<HibpPaste> {
        return try {
            val encoded = URLEncoder.encode(email, StandardCharsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://haveibeenpwned.com/api/v3/pasteaccount/$encoded")
                .header("hibp-api-key", apiKey)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            HttpClients.default.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val arr = JSONArray(response.body?.string().orEmpty())
                        (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            HibpPaste(
                                source = o.optString("Source"),
                                id = o.optString("Id"),
                                title = o.optString("Title").ifBlank { null },
                                date = o.optString("Date").ifBlank { null },
                                emailCount = o.optInt("EmailCount", 0)
                            )
                        }
                    }
                    else -> emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun checkPassword(password: String): HibpPasswordResult = withContext(Dispatchers.IO) {
        if (password.isEmpty()) {
            return@withContext HibpPasswordResult(false, 0, "", "Enter a password")
        }
        try {
            val sha1 = sha1Hex(password).uppercase()
            val prefix = sha1.take(5)
            val suffix = sha1.drop(5)
            val request = Request.Builder()
                .url("https://api.pwnedpasswords.com/range/$prefix")
                .header("User-Agent", userAgent)
                .header("Add-Padding", "true")
                .get()
                .build()
            HttpClients.default.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext HibpPasswordResult(
                        false, 0, prefix, "Pwned Passwords HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                var count = 0L
                for (line in body.lineSequence()) {
                    val parts = line.trim().split(':')
                    if (parts.size >= 2 && parts[0].equals(suffix, ignoreCase = true)) {
                        count = parts[1].trim().toLongOrNull() ?: 0L
                        break
                    }
                }
                HibpPasswordResult(pwned = count > 0, count = count, prefix = prefix)
            }
        } catch (e: Exception) {
            HibpPasswordResult(false, 0, "", e.message)
        }
    }

    suspend fun listBreaches(domainFilter: String = ""): Pair<List<HibpBreach>, String?> =
        withContext(Dispatchers.IO) {
            try {
                val url = if (domainFilter.isBlank()) {
                    "https://haveibeenpwned.com/api/v3/breaches"
                } else {
                    "https://haveibeenpwned.com/api/v3/breaches?domain=${URLEncoder.encode(domainFilter, "UTF-8")}"
                }
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .get()
                val key = getApiKey()
                if (key.isNotBlank()) builder.header("hibp-api-key", key)

                HttpClients.default.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext emptyList<HibpBreach>() to "HTTP ${response.code}"
                    }
                    val arr = JSONArray(response.body?.string().orEmpty())
                    parseBreaches(arr).sortedByDescending { it.breachDate } to null
                }
            } catch (e: Exception) {
                emptyList<HibpBreach>() to e.message
            }
        }

    private fun parseBreaches(arr: JSONArray): List<HibpBreach> {
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val classes = mutableListOf<String>()
            o.optJSONArray("DataClasses")?.let { dc ->
                for (j in 0 until dc.length()) classes += dc.getString(j)
            }
            HibpBreach(
                name = o.optString("Name"),
                title = o.optString("Title"),
                domain = o.optString("Domain"),
                breachDate = o.optString("BreachDate"),
                addedDate = o.optString("AddedDate"),
                pwnCount = o.optLong("PwnCount"),
                description = stripHtml(o.optString("Description")),
                dataClasses = classes,
                isVerified = o.optBoolean("IsVerified"),
                isSensitive = o.optBoolean("IsSensitive"),
                isRetired = o.optBoolean("IsRetired"),
                isSpamList = o.optBoolean("IsSpamList"),
                logoPath = o.optString("LogoPath").ifBlank { null }
            )
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_ENC = "api_key_enc"
        const val KEY_LEGACY = "api_key"
        const val HIBP_KEY_ALIAS = "nexus_hibp_key_wrap"
    }
}
