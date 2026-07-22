package com.example.toolkit.data.person

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PersonSearchLink(
    val source: String,
    val category: String,
    val title: String,
    val url: String,
    val note: String? = null
)

data class PersonProbe(
    val platform: String,
    val url: String,
    val status: String,
    val exists: Boolean?
)

data class PersonSearchResult(
    val query: String,
    val mode: String,
    val normalizedPhone: String? = null,
    val handles: List<String> = emptyList(),
    val links: List<PersonSearchLink> = emptyList(),
    val probes: List<PersonProbe> = emptyList(),
    val notes: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Public-source person / phone / keyword OSINT aggregator.
 * Cannot access private databases or "the entire internet" — only public URLs & platforms.
 */
class PersonSearchEngine {

    private val browserUa =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36"

    suspend fun search(
        firstName: String,
        lastName: String,
        phone: String,
        keyword: String,
        username: String,
        city: String,
        country: String
    ): PersonSearchResult = withContext(Dispatchers.IO) {
        val f = firstName.trim()
        val l = lastName.trim()
        val p = phone.trim()
        val k = keyword.trim()
        val u = username.trim().removePrefix("@")
        val cityT = city.trim()
        val countryT = country.trim()

        if (f.isBlank() && l.isBlank() && p.isBlank() && k.isBlank() && u.isBlank()) {
            return@withContext PersonSearchResult(
                query = "",
                mode = "empty",
                error = "Enter name, phone, username, or keyword"
            )
        }

        val mode = when {
            p.isNotBlank() && f.isBlank() && l.isBlank() && k.isBlank() -> "phone"
            k.isNotBlank() && f.isBlank() && l.isBlank() && p.isBlank() -> "keyword"
            u.isNotBlank() && f.isBlank() && l.isBlank() -> "username"
            else -> "person"
        }

        val fullName = listOf(f, l).filter { it.isNotBlank() }.joinToString(" ")
        val queryLabel = when (mode) {
            "phone" -> p
            "keyword" -> k
            "username" -> "@$u"
            else -> listOf(fullName, u.takeIf { it.isNotBlank() }?.let { "@$it" }, p, k)
                .filter { !it.isNullOrBlank() }
                .joinToString(" · ")
        }

        val handles = buildHandles(f, l, u, k)
        val normalizedPhone = normalizePhone(p)
        val links = mutableListOf<PersonSearchLink>()

        // Search engines — open web coverage
        val searchTerms = buildSearchTerms(fullName, normalizedPhone, u, k, cityT, countryT)
        searchTerms.forEach { term ->
            val q = enc(term)
            links += PersonSearchLink("Google", "Search engines", "Google: $term", "https://www.google.com/search?q=$q")
            links += PersonSearchLink("Bing", "Search engines", "Bing: $term", "https://www.bing.com/search?q=$q")
            links += PersonSearchLink("DuckDuckGo", "Search engines", "DDG: $term", "https://duckduckgo.com/?q=$q")
            links += PersonSearchLink("Yandex", "Search engines", "Yandex: $term", "https://yandex.com/search/?text=$q")
            links += PersonSearchLink("Brave", "Search engines", "Brave: $term", "https://search.brave.com/search?q=$q")
        }

        // Social / people directories
        if (fullName.isNotBlank()) {
            val nameQ = enc(fullName)
            links += PersonSearchLink("Facebook", "Social", "Facebook people", "https://www.facebook.com/search/people/?q=$nameQ")
            links += PersonSearchLink("LinkedIn", "Social", "LinkedIn people", "https://www.linkedin.com/search/results/people/?keywords=$nameQ")
            links += PersonSearchLink("Twitter/X", "Social", "X people search", "https://x.com/search?q=$nameQ&f=user")
            links += PersonSearchLink("Instagram", "Social", "Instagram (web search)", "https://www.google.com/search?q=site%3Ainstagram.com+$nameQ")
            links += PersonSearchLink("TikTok", "Social", "TikTok search", "https://www.tiktok.com/search/user?q=$nameQ")
            links += PersonSearchLink("VK", "Social", "VK people", "https://vk.com/search?c%5Bq%5D=$nameQ&c%5Bsection%5D=people")
            links += PersonSearchLink("Nextdoor", "Social", "Nextdoor (region)", "https://nextdoor.com/search/people/?query=$nameQ")
            links += PersonSearchLink("Meetup", "Social", "Meetup members", "https://www.meetup.com/find/?keywords=$nameQ")
        }

        handles.forEach { handle ->
            val h = enc(handle)
            links += PersonSearchLink("GitHub", "Dev / identity", "@$handle on GitHub", "https://github.com/$handle")
            links += PersonSearchLink("GitLab", "Dev / identity", "@$handle on GitLab", "https://gitlab.com/$handle")
            links += PersonSearchLink("Reddit", "Dev / identity", "u/$handle", "https://www.reddit.com/user/$handle")
            links += PersonSearchLink("Keybase", "Dev / identity", "Keybase $handle", "https://keybase.io/$handle")
            links += PersonSearchLink("Telegram", "Messaging", "t.me/$handle", "https://t.me/$handle")
            links += PersonSearchLink("About.me", "Identity", "about.me/$handle", "https://about.me/$handle")
            links += PersonSearchLink("Linktree", "Identity", "linktr.ee/$handle", "https://linktr.ee/$handle")
            links += PersonSearchLink("Gravatar", "Identity", "Gravatar $handle", "https://en.gravatar.com/$handle")
        }

        // Phone-focused
        if (normalizedPhone != null) {
            val phoneQ = enc(normalizedPhone)
            val digits = normalizedPhone.filter { it.isDigit() }
            links += PersonSearchLink("Google Phone", "Phone", "Google \"$normalizedPhone\"", "https://www.google.com/search?q=%22$phoneQ%22")
            links += PersonSearchLink("Bing Phone", "Phone", "Bing phone", "https://www.bing.com/search?q=%22$phoneQ%22")
            links += PersonSearchLink("Truecaller Web", "Phone", "Truecaller search", "https://www.truecaller.com/search/ro/$digits")
            links += PersonSearchLink("Sync.me", "Phone", "Sync.me", "https://sync.me/search/?number=$digits")
            links += PersonSearchLink("WhoCallsMe", "Phone", "WhoCallsMe", "https://whocallsme.com/Phone-Number.aspx/$digits")
            links += PersonSearchLink("Telegram", "Phone", "Telegram (contacts)", "https://t.me/+$digits")
            links += PersonSearchLink("WhatsApp", "Phone", "wa.me link", "https://wa.me/$digits")
            links += PersonSearchLink("Viber", "Phone", "Viber chat link", "viber://chat?number=%2B$digits")
            links += PersonSearchLink("Facebook Phone", "Phone", "FB phone dork", "https://www.google.com/search?q=site%3Afacebook.com+$phoneQ")
        }

        // Keyword / paste / docs
        val kw = k.ifBlank { fullName }.ifBlank { u }
        if (kw.isNotBlank()) {
            val kq = enc(kw)
            links += PersonSearchLink("Pastebin", "Leaks / pastes", "Pastebin search", "https://pastebin.com/search?q=$kq")
            links += PersonSearchLink("GitHub Code", "Leaks / pastes", "GitHub code search", "https://github.com/search?q=$kq&type=code")
            links += PersonSearchLink("GitLab Search", "Leaks / pastes", "GitLab search", "https://gitlab.com/search?search=$kq")
            links += PersonSearchLink("Internet Archive", "Archives", "Wayback keyword", "https://web.archive.org/web/*/$kq")
            links += PersonSearchLink("Wikipedia", "Reference", "Wikipedia", "https://en.wikipedia.org/w/index.php?search=$kq")
            links += PersonSearchLink("News", "News", "Google News", "https://news.google.com/search?q=$kq")
            links += PersonSearchLink("Scholar", "Academic", "Google Scholar", "https://scholar.google.com/scholar?q=$kq")
            links += PersonSearchLink("CourtListener", "Public records", "CourtListener", "https://www.courtlistener.com/?q=$kq")
            links += PersonSearchLink("OpenCorporates", "Public records", "OpenCorporates", "https://opencorporates.com/companies?q=$kq")
        }

        // Google dorks
        if (fullName.isNotBlank()) {
            val n = enc("\"$fullName\"")
            links += PersonSearchLink("Dork PDF", "Dorks", "PDFs with name", "https://www.google.com/search?q=$n+filetype%3Apdf")
            links += PersonSearchLink("Dork Docs", "Dorks", "Docs with name", "https://www.google.com/search?q=$n+(filetype%3Adoc+OR+filetype%3Adocx+OR+filetype%3Axlsx)")
            links += PersonSearchLink("Dork Email", "Dorks", "Email mentions", "https://www.google.com/search?q=$n+email+OR+%40+OR+contact")
        }

        val probes = if (handles.isNotEmpty()) {
            probeTopPlatforms(handles.first())
        } else emptyList()

        val notes = buildList {
            add("Searches PUBLIC sources only — search engines, social, paste sites, archives.")
            add("No private DB / carrier / government unrestricted access exists from a mobile app.")
            add("Tap any result to open it in the browser and continue investigation.")
            if (normalizedPhone != null) add("Phone normalized: $normalizedPhone")
            if (handles.isNotEmpty()) add("Handles tried: " + handles.joinToString(", ") { "@$it" })
            add("Probes live: ${probes.count { it.exists == true }} hits / ${probes.size} checked")
        }

        PersonSearchResult(
            query = queryLabel,
            mode = mode,
            normalizedPhone = normalizedPhone,
            handles = handles,
            links = links.distinctBy { it.url },
            probes = probes,
            notes = notes
        )
    }

    private fun buildSearchTerms(
        fullName: String,
        phone: String?,
        username: String,
        keyword: String,
        city: String,
        country: String
    ): List<String> {
        val terms = linkedSetOf<String>()
        if (fullName.isNotBlank()) {
            terms += "\"$fullName\""
            if (city.isNotBlank()) terms += "\"$fullName\" $city"
            if (country.isNotBlank()) terms += "\"$fullName\" $country"
            if (city.isNotBlank() && country.isNotBlank()) terms += "\"$fullName\" $city $country"
        }
        if (username.isNotBlank()) terms += "\"$username\""
        if (phone != null) terms += "\"$phone\""
        if (keyword.isNotBlank()) terms += keyword
        return terms.take(6).toList()
    }

    private fun buildHandles(first: String, last: String, username: String, keyword: String): List<String> {
        val out = linkedSetOf<String>()
        fun add(raw: String) {
            val c = raw.lowercase().replace(Regex("[^a-z0-9._-]"), "").trim('.', '-', '_')
            if (c.length in 2..32) out += c
        }
        if (username.isNotBlank()) add(username)
        val f = first.lowercase().filter { it.isLetterOrDigit() }
        val l = last.lowercase().filter { it.isLetterOrDigit() }
        if (f.isNotBlank() && l.isNotBlank()) {
            add("$f$l"); add("$f.$l"); add("${f}_$l"); add("${f.first()}$l"); add("$l$f")
        }
        if (keyword.isNotBlank() && !keyword.contains(' ')) add(keyword)
        return out.toList().take(6)
    }

    private fun normalizePhone(raw: String): String? {
        if (raw.isBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 8) return null
        return if (raw.trim().startsWith("+")) "+$digits" else digits
    }

    private suspend fun probeTopPlatforms(handle: String): List<PersonProbe> = coroutineScope {
        val targets = listOf(
            "GitHub" to "https://github.com/$handle",
            "GitLab" to "https://gitlab.com/$handle",
            "Reddit" to "https://www.reddit.com/user/$handle",
            "Telegram" to "https://t.me/$handle",
            "Keybase" to "https://keybase.io/$handle",
            "Dev.to" to "https://dev.to/$handle",
            "About.me" to "https://about.me/$handle",
            "Linktree" to "https://linktr.ee/$handle"
        )
        val sem = Semaphore(6)
        targets.map { (name, url) ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val exists = probeExists(url)
                    PersonProbe(
                        platform = name,
                        url = url,
                        status = when (exists) {
                            true -> "HIT"
                            false -> "MISS"
                            null -> "?"
                        },
                        exists = exists
                    )
                }
            }
        }.awaitAll()
    }

    private fun probeExists(url: String): Boolean? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUa)
                .get()
                .build()
            HttpClients.noRedirect.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> true
                    404, 410 -> false
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
