package com.example.toolkit.data.hashcrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class HashGuess(val algo: String, val confidence: String)

data class CrackResult(
    val input: String,
    val normalized: String,
    val guesses: List<HashGuess> = emptyList(),
    val cracked: String? = null,
    val crackedAlgo: String? = null,
    val tried: Int = 0,
    val note: String? = null,
    val error: String? = null
)

/**
 * Local hash identifier + dictionary attack (like hashid + a mini hashcat in
 * straight/dictionary mode). Everything runs on-device: it guesses the hash
 * type from length/charset, then hashes each candidate from a built-in common
 * password list (plus any extra words supplied) and compares.
 */
class HashCrackEngine {

    /** Fast, unsalted algorithms we can attack by recomputing per candidate. */
    private val hexAlgos = mapOf(
        32 to listOf("MD5" to "MD5", "MD4/NTLM" to null),
        40 to listOf("SHA-1" to "SHA-1"),
        56 to listOf("SHA-224" to "SHA-224"),
        64 to listOf("SHA-256" to "SHA-256"),
        96 to listOf("SHA-384" to "SHA-384"),
        128 to listOf("SHA-512" to "SHA-512")
    )

    suspend fun crack(rawHash: String, extraWords: String = ""): CrackResult = withContext(Dispatchers.Default) {
        val normalized = rawHash.trim()
        if (normalized.isBlank()) return@withContext CrackResult(rawHash, "", error = "Introdu un hash")

        val guesses = identify(normalized)
        val lower = normalized.lowercase()
        val isHex = lower.matches(Regex("^[a-f0-9]+$"))

        if (normalized.startsWith("\$2a$") || normalized.startsWith("\$2b$") || normalized.startsWith("\$2y$")) {
            return@withContext CrackResult(
                rawHash, normalized, guesses,
                note = "bcrypt detectat — atacul de dicționar necesită verificare bcrypt (nerulat aici); folosește doar identificarea."
            )
        }
        if (!isHex) {
            return@withContext CrackResult(
                rawHash, normalized, guesses,
                note = "Hash-ul nu e hex simplu — dictionary attack acceptat doar pentru MD5/SHA-x în format hexazecimal."
            )
        }

        // Algorithms whose digest length matches the input; if unknown, try all.
        val candidateAlgos = hexAlgos[normalized.length]
            ?.mapNotNull { it.second }
            ?: listOf("MD5", "SHA-1", "SHA-256", "SHA-512")

        val wordlist = buildWordlist(extraWords)
        var tried = 0
        for (word in wordlist) {
            for (algo in candidateAlgos) {
                tried++
                if (digestHex(algo, word) == lower) {
                    return@withContext CrackResult(
                        rawHash, normalized, guesses,
                        cracked = word, crackedAlgo = algo, tried = tried
                    )
                }
            }
        }

        CrackResult(
            rawHash, normalized, guesses, tried = tried,
            note = "Necrackat cu ${wordlist.size} candidați. Adaugă cuvinte proprii pentru un dicționar mai mare."
        )
    }

    private fun identify(hash: String): List<HashGuess> {
        val h = hash.trim()
        val lower = h.lowercase()
        return when {
            h.startsWith("\$2a$") || h.startsWith("\$2b$") || h.startsWith("\$2y$") ->
                listOf(HashGuess("bcrypt", "sigur"))
            h.startsWith("\$1$") -> listOf(HashGuess("MD5-crypt (Unix)", "sigur"))
            h.startsWith("\$6$") -> listOf(HashGuess("SHA-512-crypt (Unix)", "sigur"))
            h.startsWith("\$5$") -> listOf(HashGuess("SHA-256-crypt (Unix)", "sigur"))
            h.startsWith("\$argon2") -> listOf(HashGuess("Argon2", "sigur"))
            h.contains(":") && lower.substringBefore(":").matches(Regex("^[a-f0-9]{32}$")) ->
                listOf(HashGuess("LM:NTLM (32:32)", "probabil"))
            !lower.matches(Regex("^[a-f0-9]+$")) ->
                listOf(HashGuess("Non-hex (Base64? / cu sare?)", "necunoscut"))
            else -> when (h.length) {
                32 -> listOf(HashGuess("MD5", "probabil"), HashGuess("NTLM / MD4", "posibil"))
                40 -> listOf(HashGuess("SHA-1", "probabil"))
                56 -> listOf(HashGuess("SHA-224", "probabil"))
                64 -> listOf(HashGuess("SHA-256", "probabil"), HashGuess("SHA3-256", "posibil"))
                96 -> listOf(HashGuess("SHA-384", "probabil"))
                128 -> listOf(HashGuess("SHA-512", "probabil"), HashGuess("SHA3-512", "posibil"))
                else -> listOf(HashGuess("Necunoscut (${h.length} caractere hex)", "necunoscut"))
            }
        }
    }

    private fun digestHex(algo: String, input: String): String {
        val md = MessageDigest.getInstance(algo)
        return md.digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildWordlist(extra: String): List<String> {
        val extras = extra.split(Regex("[\\n,;\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
        return (extras + COMMON).distinct()
    }

    companion object {
        /** Condensed rockyou-style top passwords for on-device dictionary attacks. */
        private val COMMON: List<String> = listOf(
            "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234",
            "111111", "1234567", "dragon", "123123", "baseball", "abc123", "football",
            "monkey", "letmein", "shadow", "master", "666666", "qwertyuiop", "123321",
            "mustang", "1234567890", "michael", "654321", "superman", "1qaz2wsx",
            "7777777", "121212", "000000", "qazwsx", "123qwe", "killer", "trustno1",
            "jordan", "jennifer", "zxcvbnm", "asdfgh", "hunter", "buster", "soccer",
            "harley", "batman", "andrew", "tigger", "sunshine", "iloveyou", "2000",
            "charlie", "robert", "thomas", "hockey", "ranger", "daniel", "starwars",
            "klaster", "112233", "george", "computer", "michelle", "jessica", "pepper",
            "1111", "zxcvbn", "555555", "11111111", "131313", "freedom", "777777",
            "pass", "maggie", "159753", "aaaaaa", "ginger", "princess", "joshua",
            "cheese", "amanda", "summer", "love", "ashley", "nicole", "chelsea",
            "biteme", "matthew", "access", "yankees", "987654321", "dallas", "austin",
            "thunder", "taylor", "matrix", "mobilemail", "mom", "monitor", "monitoring",
            "montana", "moon", "moscow", "admin", "administrator", "root", "toor",
            "welcome", "welcome1", "password1", "password123", "passw0rd", "p@ssw0rd",
            "P@ssw0rd", "Passw0rd", "admin123", "root123", "changeme", "secret", "test",
            "test123", "guest", "oracle", "postgres", "mysql", "redhat", "linux",
            "hello", "hello123", "abcd1234", "qwerty123", "1q2w3e4r", "1qazxsw2",
            "google", "facebook", "internet", "service", "letmein123", "iloveyou1",
            "whatever", "trustme", "flower", "hottie", "loveme", "zaq12wsx", "qwe123",
            "asdf", "asdfasdf", "temp123", "demo", "default", "system", "manager"
        )
    }
}
