package dev.johnoreilly.wordmaster.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Validates English words against the Free Dictionary API.
 * https://dictionaryapi.dev
 *
 * Returns true if the word exists in the English dictionary.
 * Returns false if the word doesn't exist OR if the network is unavailable.
 *
 * This is used as a fallback when a guess is not found in the local words.txt.
 */
class OnlineWordValidator {

    private val client = HttpClient()

    /**
     * Returns true if [word] is a valid English word according to the Free Dictionary API.
     * This call should be wrapped in try/catch by the caller, but internally it never throws
     * (returns false on any failure).
     */
    suspend fun isValidWord(word: String): Boolean {
        return try {
            val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${word.lowercase()}"
            val response = client.get(url)
            val isValid = response.status.isSuccess()
            println("[OnlineWordValidator] '${word}' → HTTP ${response.status.value} → valid=$isValid")
            isValid
        } catch (e: Exception) {
            println("[OnlineWordValidator] offline/error for '${word}': ${e.message}")
            false  // treat network failure as "unknown word, reject"
        }
    }
}
