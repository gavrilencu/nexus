package com.example.toolkit.data.osint

import com.example.toolkit.data.model.OsintProfile
import com.example.toolkit.data.model.OsintResult
import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Username OSINT probes with content-aware checks (avoids SPA false positives).
 * Authorized / public-source use only.
 */
class OsintEngine {

    private enum class CheckMode {
        STATUS,          // trust HTTP status alone
        BODY,            // parse HTML/JSON body markers
        JSON_API,        // dedicated API endpoint
        REDIRECT         // 3xx without follow = usually missing / login wall
    }

    private data class Platform(
        val name: String,
        val profileUrl: String,
        val checkUrl: String = profileUrl,
        val mode: CheckMode = CheckMode.BODY,
        val errorCodes: Set<Int> = setOf(404, 410),
        val okCodes: Set<Int> = setOf(200),
        val notFoundBody: List<String> = emptyList(),
        val foundBody: List<String> = emptyList(),
        val requireFoundMarker: Boolean = false
    )

    private val browserUa =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient = HttpClients.default.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val platforms = listOf(
        Platform(
            name = "GitHub",
            profileUrl = "https://github.com/%s",
            checkUrl = "https://api.github.com/users/%s",
            mode = CheckMode.JSON_API,
            foundBody = listOf("\"login\""),
            notFoundBody = listOf("Not Found", "\"message\":\"Not Found\"")
        ),
        Platform(
            name = "GitLab",
            profileUrl = "https://gitlab.com/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("Page not found", "404", "Whoops"),
            foundBody = listOf("og:title", "user-profile")
        ),
        Platform(
            name = "Reddit",
            profileUrl = "https://www.reddit.com/user/%s",
            checkUrl = "https://www.reddit.com/user/%s/about.json",
            mode = CheckMode.JSON_API,
            foundBody = listOf("\"kind\": \"t2\"", "\"name\""),
            notFoundBody = listOf("\"error\": 404", "Not Found", "PAGE_NOT_FOUND")
        ),
        Platform(
            name = "HackerNews",
            profileUrl = "https://news.ycombinator.com/user?id=%s",
            checkUrl = "https://hacker-news.firebaseio.com/v0/user/%s.json",
            mode = CheckMode.JSON_API,
            foundBody = listOf("\"id\":", "\"created\":"),
            notFoundBody = listOf("null")
        ),
        Platform(
            name = "Keybase",
            profileUrl = "https://keybase.io/%s",
            checkUrl = "https://keybase.io/_/api/1.0/user/lookup.json?username=%s",
            mode = CheckMode.JSON_API,
            foundBody = listOf("\"them\":", "\"basics\""),
            notFoundBody = listOf("\"them\":null", "not found", "\"status\":{\"code\":3")
        ),
        Platform(
            name = "Dev.to",
            profileUrl = "https://dev.to/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("This page does not exist", "404"),
            foundBody = listOf("profile-header", "user-profile-header", "og:title")
        ),
        Platform(
            name = "Medium",
            profileUrl = "https://medium.com/@%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("PAGE_NOT_FOUND", "Out of nothing", "410"),
            foundBody = listOf("\"@type\":\"Person\"", "profileMeta")
        ),
        Platform(
            name = "Instagram",
            profileUrl = "https://www.instagram.com/%s/",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf(
                "Sorry, this page isn't available",
                "The link you followed may be broken",
                "page isn't available"
            ),
            foundBody = listOf(
                "\"profile_pic_url\"",
                "\"edge_followed_by\"",
                "og:description\" content=\"",
                "property=\"og:type\" content=\"profile\""
            )
        ),
        Platform(
            name = "Twitter/X",
            profileUrl = "https://x.com/%s",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf(
                "This account doesn’t exist",
                "This account doesn't exist",
                "Account suspended",
                "Try searching for another"
            ),
            foundBody = listOf(
                "profile_button",
                "\"screen_name\"",
                "og:title"
            )
        ),
        Platform(
            name = "TikTok",
            profileUrl = "https://www.tiktok.com/@%s",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf(
                "Couldn't find this account",
                "page not available",
                "statusCode\":10221",
                "user doesn't exist"
            ),
            foundBody = listOf(
                "\"uniqueId\"",
                "shareMeta",
                "og:title"
            )
        ),
        Platform(
            name = "YouTube",
            profileUrl = "https://www.youtube.com/@%s",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf(
                "This page isn't available",
                "404 Not Found",
                "channel does not exist"
            ),
            foundBody = listOf(
                "\"channelId\"",
                "og:video:tag",
                "subscriberCountText"
            )
        ),
        Platform(
            name = "Twitch",
            profileUrl = "https://www.twitch.tv/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("Sorry. Unless you've got a time machine", "404"),
            foundBody = listOf("og:title", "twilight-user")
        ),
        Platform(
            name = "Pinterest",
            profileUrl = "https://www.pinterest.com/%s/",
            mode = CheckMode.BODY,
            notFoundBody = listOf("Sorry! We couldn't find", "page not found"),
            foundBody = listOf("og:title", "UserProfile")
        ),
        Platform(
            name = "Steam",
            profileUrl = "https://steamcommunity.com/id/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf(
                "The specified profile could not be found",
                "error_ctn"
            ),
            foundBody = listOf("profile_header", "steamID")
        ),
        Platform(
            name = "SoundCloud",
            profileUrl = "https://soundcloud.com/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("We can't find that user", "404"),
            foundBody = listOf("og:title", "soundcloud:user")
        ),
        Platform(
            name = "Pastebin",
            profileUrl = "https://pastebin.com/u/%s",
            mode = CheckMode.STATUS,
            errorCodes = setOf(404, 410)
        ),
        Platform(
            name = "About.me",
            profileUrl = "https://about.me/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("page not found", "404"),
            foundBody = listOf("og:title", "aboutme")
        ),
        Platform(
            name = "Flipboard",
            profileUrl = "https://flipboard.com/@%s",
            mode = CheckMode.STATUS
        ),
        Platform(
            name = "Roblox",
            profileUrl = "https://www.roblox.com/users/profile?username=%s",
            checkUrl = "https://www.roblox.com/users/profile?username=%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("Page cannot be found", "404"),
            foundBody = listOf("profile-header", "data-profileuserid")
        ),
        Platform(
            name = "Chess.com",
            profileUrl = "https://www.chess.com/member/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("User not found", "404"),
            foundBody = listOf("profile-card", "og:title")
        ),
        Platform(
            name = "Codeforces",
            profileUrl = "https://codeforces.com/profile/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("not found", "Codeforces is temporarily unavailable"),
            foundBody = listOf("info", "user-box")
        ),
        Platform(
            name = "LeetCode",
            profileUrl = "https://leetcode.com/u/%s/",
            checkUrl = "https://leetcode.com/graphql",
            mode = CheckMode.BODY,
            notFoundBody = listOf("user does not exist", "404"),
            foundBody = listOf("userAvatar", "profile")
        ),
        Platform(
            name = "Docker Hub",
            profileUrl = "https://hub.docker.com/u/%s",
            checkUrl = "https://hub.docker.com/v2/users/%s/",
            mode = CheckMode.JSON_API,
            foundBody = listOf("\"username\"", "\"id\""),
            notFoundBody = listOf("Not Found", "\"message\"")
        ),
        Platform(
            name = "NPM",
            profileUrl = "https://www.npmjs.com/~%s",
            mode = CheckMode.STATUS
        ),
        Platform(
            name = "PyPI",
            profileUrl = "https://pypi.org/user/%s/",
            mode = CheckMode.STATUS
        ),
        Platform(
            name = "Bitbucket",
            profileUrl = "https://bitbucket.org/%s/",
            mode = CheckMode.STATUS
        ),
        Platform(
            name = "Replit",
            profileUrl = "https://replit.com/@%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("404", "not found"),
            foundBody = listOf("og:title", "profile")
        ),
        Platform(
            name = "Linktree",
            profileUrl = "https://linktr.ee/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("The page you're looking for doesn't exist", "404"),
            foundBody = listOf("og:title", "linktree")
        ),
        Platform(
            name = "Telegram",
            profileUrl = "https://t.me/%s",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf("If you have Telegram", "tgme_page_additional"),
            foundBody = listOf("tgme_page_photo", "tgme_page_title")
        ),
        Platform(
            name = "VK",
            profileUrl = "https://vk.com/%s",
            mode = CheckMode.BODY,
            notFoundBody = listOf("page not found", "404 Not Found", "Profile deleted"),
            foundBody = listOf("og:title", "page_name")
        ),
        Platform(
            name = "Facebook",
            profileUrl = "https://www.facebook.com/%s",
            checkUrl = "https://www.facebook.com/%s",
            mode = CheckMode.BODY,
            requireFoundMarker = true,
            notFoundBody = listOf(
                "This content isn't available right now",
                "content isn't available",
                "The page you requested cannot be displayed",
                "Sorry, this content isn't available",
                "isn't available"
            ),
            foundBody = listOf(
                "property=\"og:title\"",
                "\"userID\"",
                "\"profile_id\"",
                "property=\"og:type\" content=\"profile\"",
                "content=\"profile\""
            )
        )
    )

    suspend fun search(
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        company: String
    ): OsintResult = withContext(Dispatchers.IO) {
        val handles = buildHandleCandidates(firstName, lastName, username, email)
        if (handles.isEmpty() && email.isBlank()) {
            return@withContext OsintResult(
                query = "",
                queryType = "empty",
                error = "Provide username, name, or email"
            )
        }

        val queryLabel = buildString {
            if (firstName.isNotBlank() || lastName.isNotBlank()) {
                append("$firstName $lastName".trim())
            }
            if (username.isNotBlank()) {
                if (isNotEmpty()) append(" / ")
                append("@$username")
            }
            if (email.isNotBlank()) {
                if (isNotEmpty()) append(" / ")
                append(email)
            }
        }

        try {
            val primary = handles.firstOrNull().orEmpty()
            val profiles = if (handles.isNotEmpty()) {
                // Probe primary handle across all platforms; also probe alt handles on top sources
                val primaryHits = checkPlatforms(primary)
                val altHandles = handles.drop(1).take(3)
                val altHits = if (altHandles.isNotEmpty()) {
                    checkPlatformsForHandles(altHandles, topPlatformsOnly = true)
                } else emptyList()
                (primaryHits + altHits)
                    .distinctBy { "${it.platform.lowercase()}|${it.username.lowercase()}" }
                    .sortedWith(
                        compareByDescending<OsintProfile> { it.exists == true }
                            .thenByDescending { it.confidence }
                            .thenBy { it.platform }
                    )
            } else emptyList()

            val patterns = buildEmailPatterns(firstName, lastName, company, email)
            val notes = buildNotes(firstName, lastName, handles, email, company, profiles)

            OsintResult(
                query = queryLabel,
                queryType = when {
                    username.isNotBlank() -> "username"
                    email.isNotBlank() -> "email"
                    else -> "name"
                },
                handlesTried = handles,
                profiles = profiles,
                emailPatterns = patterns,
                notes = notes
            )
        } catch (e: Exception) {
            OsintResult(query = queryLabel, queryType = "error", error = e.message)
        }
    }

    private fun buildHandleCandidates(
        first: String,
        last: String,
        username: String,
        email: String
    ): List<String> {
        val out = linkedSetOf<String>()
        fun add(raw: String) {
            val cleaned = raw.trim().lowercase()
                .replace(" ", "")
                .replace(Regex("[^a-z0-9._-]"), "")
                .trim('.', '-', '_')
            if (cleaned.length in 2..32) out += cleaned
        }

        if (username.isNotBlank()) add(username.removePrefix("@"))
        if (email.contains("@")) add(email.substringBefore("@"))

        val f = first.lowercase().filter { it.isLetterOrDigit() }
        val l = last.lowercase().filter { it.isLetterOrDigit() }
        if (f.isNotBlank() && l.isNotBlank()) {
            add("$f$l")
            add("$f.$l")
            add("${f}_$l")
            add("$f-$l")
            add("${f.first()}$l")
            add("$f${l.first()}")
            add("$l$f")
            add("$l.$f")
        } else if (f.isNotBlank()) {
            add(f)
        } else if (l.isNotBlank()) {
            add(l)
        }
        return out.toList()
    }

    private suspend fun checkPlatforms(username: String): List<OsintProfile> =
        checkPlatformsForHandles(listOf(username), topPlatformsOnly = false)

    private suspend fun checkPlatformsForHandles(
        handles: List<String>,
        topPlatformsOnly: Boolean
    ): List<OsintProfile> = coroutineScope {
        val list = if (topPlatformsOnly) {
            platforms.filter {
                it.name in setOf(
                    "GitHub", "GitLab", "Reddit", "Instagram", "Twitter/X",
                    "TikTok", "YouTube", "Telegram", "Dev.to", "Keybase", "Facebook"
                )
            }
        } else platforms

        val semaphore = Semaphore(10)
        handles.flatMap { handle ->
            list.map { platform ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { probe(platform, handle) }
                }
            }
        }.awaitAll()
    }

    private fun probe(platform: Platform, username: String): OsintProfile {
        val profileUrl = platform.profileUrl.format(username)
        val checkUrl = platform.checkUrl.format(username)

        // LeetCode GraphQL is awkward without POST body — fall back to profile page
        val effective = if (platform.name == "LeetCode") {
            platform.copy(
                checkUrl = "https://leetcode.com/%s/",
                mode = CheckMode.BODY
            )
        } else platform

        return try {
            when (effective.mode) {
                CheckMode.STATUS -> probeByStatus(effective, username, profileUrl, checkUrl)
                CheckMode.JSON_API -> probeByBody(effective, username, profileUrl, checkUrl, json = true)
                CheckMode.BODY -> probeByBody(effective, username, profileUrl, checkUrl, json = false)
                CheckMode.REDIRECT -> probeByRedirect(effective, username, profileUrl, checkUrl)
            }
        } catch (e: Exception) {
            OsintProfile(
                platform = platform.name,
                username = username,
                url = profileUrl,
                exists = null,
                confidence = 0,
                note = "Error: ${e.message ?: "probe failed"}"
            )
        }
    }

    private fun probeByStatus(
        platform: Platform,
        username: String,
        profileUrl: String,
        checkUrl: String
    ): OsintProfile {
        val response = execute(checkUrl, followRedirects = true) ?: return inconclusive(platform, username, profileUrl, "No response")
        response.use { resp ->
            val code = resp.code
            return when {
                code in platform.errorCodes -> miss(platform, username, profileUrl, "HTTP $code")
                code in platform.okCodes -> hit(platform, username, profileUrl, 80, "HTTP $code")
                code in 300..399 -> inconclusive(platform, username, profileUrl, "HTTP $code redirect")
                code == 403 || code == 401 || code == 429 ->
                    inconclusive(platform, username, profileUrl, "HTTP $code blocked/rate-limited")
                else -> inconclusive(platform, username, profileUrl, "HTTP $code")
            }
        }
    }

    private fun probeByBody(
        platform: Platform,
        username: String,
        profileUrl: String,
        checkUrl: String,
        json: Boolean
    ): OsintProfile {
        val response = execute(checkUrl, followRedirects = true)
            ?: return inconclusive(platform, username, profileUrl, "No response")

        response.use { resp ->
            val code = resp.code
            if (code in platform.errorCodes) {
                return miss(platform, username, profileUrl, "HTTP $code")
            }
            if (code == 403 || code == 401 || code == 429) {
                return inconclusive(platform, username, profileUrl, "HTTP $code blocked/rate-limited")
            }

            val body = resp.body?.string().orEmpty()

            // Explicit not-found markers win
            if (platform.notFoundBody.any { body.contains(it, ignoreCase = true) }) {
                return miss(platform, username, profileUrl, "Not-found marker in page")
            }
            // JSON null body (HN / some APIs)
            if (json && body.trim() == "null") {
                return miss(platform, username, profileUrl, "API returned null")
            }

            val found = platform.foundBody.any { body.contains(it, ignoreCase = true) }
            if (found) {
                val confidence = if (json) 95 else 88
                return hit(platform, username, profileUrl, confidence, "Profile markers confirmed")
            }

            if (platform.requireFoundMarker) {
                // SPA sites like IG/X often return 200 login walls — do NOT treat as HIT
                return inconclusive(
                    platform,
                    username,
                    profileUrl,
                    "HTTP $code but no profile proof (login wall / SPA)"
                )
            }

            if (code in platform.okCodes && body.isNotBlank()) {
                // Weak signal only
                return inconclusive(platform, username, profileUrl, "HTTP $code without clear markers")
            }

            return inconclusive(platform, username, profileUrl, "HTTP $code")
        }
    }

    private fun probeByRedirect(
        platform: Platform,
        username: String,
        profileUrl: String,
        checkUrl: String
    ): OsintProfile {
        val response = execute(checkUrl, followRedirects = false)
            ?: return inconclusive(platform, username, profileUrl, "No response")
        response.use { resp ->
            return when (resp.code) {
                in platform.errorCodes -> miss(platform, username, profileUrl, "HTTP ${resp.code}")
                in 200..299 -> hit(platform, username, profileUrl, 75, "HTTP ${resp.code}")
                in 300..399 -> miss(platform, username, profileUrl, "Redirected (${resp.code})")
                else -> inconclusive(platform, username, profileUrl, "HTTP ${resp.code}")
            }
        }
    }

    private fun execute(url: String, followRedirects: Boolean): okhttp3.Response? {
        val http = if (followRedirects) client else noRedirectClient
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", browserUa)
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        return try {
            http.newCall(request).execute()
        } catch (_: Exception) {
            null
        }
    }

    private fun hit(
        platform: Platform,
        username: String,
        url: String,
        confidence: Int,
        note: String
    ) = OsintProfile(
        platform = platform.name,
        username = username,
        url = url,
        exists = true,
        confidence = confidence,
        note = note
    )

    private fun miss(
        platform: Platform,
        username: String,
        url: String,
        note: String
    ) = OsintProfile(
        platform = platform.name,
        username = username,
        url = url,
        exists = false,
        confidence = 90,
        note = note
    )

    private fun inconclusive(
        platform: Platform,
        username: String,
        url: String,
        note: String
    ) = OsintProfile(
        platform = platform.name,
        username = username,
        url = url,
        exists = null,
        confidence = 20,
        note = note
    )

    private fun buildEmailPatterns(
        first: String,
        last: String,
        company: String,
        email: String
    ): List<String> {
        val f = first.lowercase().filter { it.isLetter() }
        val l = last.lowercase().filter { it.isLetter() }
        val domain = when {
            email.contains("@") -> email.substringAfter("@")
            company.isNotBlank() -> company.lowercase()
                .replace(Regex("[^a-z0-9.]"), "")
                .let { value ->
                    when {
                        value.contains('.') -> value
                        value.isNotBlank() -> "$value.com"
                        else -> ""
                    }
                }
            else -> ""
        }
        if (f.isBlank() || l.isBlank() || domain.isBlank()) {
            return if (email.isNotBlank()) listOf(email) else emptyList()
        }
        return listOf(
            "$f.$l@$domain",
            "$f$l@$domain",
            "${f.first()}.$l@$domain",
            "${f.first()}$l@$domain",
            "$f@$domain",
            "$l@$domain",
            "$f-$l@$domain",
            "${f}_$l@$domain"
        ).distinct()
    }

    private fun buildNotes(
        first: String,
        last: String,
        handles: List<String>,
        email: String,
        company: String,
        profiles: List<OsintProfile>
    ): List<String> {
        val notes = mutableListOf<String>()
        notes += "Authorized OSINT only — verify hits manually before action."
        if (handles.isNotEmpty()) {
            notes += "Handles probed: " + handles.joinToString(", ") { "@$it" }
        }
        if (first.isNotBlank() || last.isNotBlank()) {
            notes += "Name variants: ${"$first $last".trim()}, ${"$last $first".trim()}"
        }
        if (company.isNotBlank()) notes += "Org context: $company"
        if (email.isNotBlank()) notes += "Seed email: $email"
        val hits = profiles.count { it.exists == true }
        val miss = profiles.count { it.exists == false }
        val unk = profiles.count { it.exists == null }
        notes += "Results: $hits confirmed / $miss not found / $unk inconclusive (of ${profiles.size})"
        notes += "Instagram/X/TikTok need profile markers — login walls are NOT counted as hits."
        return notes
    }
}
