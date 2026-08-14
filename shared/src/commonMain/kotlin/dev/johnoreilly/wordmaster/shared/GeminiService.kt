package dev.johnoreilly.wordmaster.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Lightweight Gemini REST API client using Ktor (KMP-compatible).
 * Works on Android, iOS, and Desktop without any platform-specific code.
 *
 * API key is passed as a constructor parameter — the platform layer is responsible
 * for reading the key from its own secure storage (BuildConfig, plist, env var, etc.).
 */
class GeminiService(private val apiKey: String) {

    private val model = "gemini-2.0-flash"
    private val endpoint
        get() = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    // Ktor picks up the platform-specific engine automatically
    // (OkHttp on Android/JVM, Darwin on iOS)
    private val client = HttpClient()

    companion object {
        /** Fallback shown to user if the API call fails or the key is wrong. */
        const val FALLBACK =
            "Think about letters you haven't tried yet. Try a word rich in new vowels!"
        const val FALLBACK_VI =
            "Hãy nghĩ về những chữ cái bạn chưa thử. Hãy thử một từ có nhiều nguyên âm mới!"
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Calls Gemini and returns a smart semantic hint about [answer] without revealing it.
     * The hint language follows [language]: EN = English, VI = Vietnamese.
     * Always returns a non-null, non-empty string (falls back gracefully on any error).
     */
    suspend fun generateSmartHint(
        answer: String,
        boardGuesses: List<List<String>>,
        keyStatus: Map<String, LetterStatus>,
        language: AppLanguage = AppLanguage.EN
    ): String {
        return try {
            val prompt      = buildPrompt(answer, boardGuesses, keyStatus, language)
            val responseJson = callApi(prompt)
            // Log raw response for debugging (visible in Logcat)
            println("[GeminiService] raw response: ${responseJson.take(300)}")
            val hint = extractText(responseJson, language)
            sanitize(hint, answer, language)
        } catch (e: Exception) {
            println("[GeminiService] exception: ${e.message}")
            if (language == AppLanguage.VI) FALLBACK_VI else FALLBACK
        }
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    private fun buildPrompt(
        answer: String,
        boardGuesses: List<List<String>>,
        keyStatus: Map<String, LetterStatus>,
        language: AppLanguage = AppLanguage.EN
    ): String {
        val guessedWords = boardGuesses
            .map { it.joinToString("") }
            .filter { it.length == 5 }
            .joinToString(", ")
            .ifEmpty { if (language == AppLanguage.VI) "chưa có từ nào" else "none yet" }

        val confirmed = keyStatus.entries
            .filter { it.value == LetterStatus.CORRECT_POSITION }
            .joinToString(", ") { it.key }
            .ifEmpty { if (language == AppLanguage.VI) "chưa có" else "none" }

        val eliminated = keyStatus.entries
            .filter { it.value == LetterStatus.NOT_IN_WORD }
            .joinToString(", ") { it.key }
            .ifEmpty { if (language == AppLanguage.VI) "chưa có" else "none" }

        return if (language == AppLanguage.VI) {
            """
Bạn là trợ lý AI cho trò chơi đoán từ WordMaster.
TỪ BÍ MẬT CẦN ĐOÁN LÀ: "$answer" (đây là một từ tiếng Anh 5 chữ cái).

Trạng thái bàn chơi hiện tại:
- Các từ người chơi đã đoán: $guessedWords
- Các chữ cái đúng vị trí: $confirmed
- Các chữ cái KHÔNG có trong từ: $eliminated

NHỆM VỤ CỦA BẠN:
Viết duy nhất 01 câu gợi ý thông minh về NGHĨA, CHỦ ĐỀ hoặc BỐI CẢNH SỬ DỤNG của từ bí mật này BẰNG TIẾNG VIỆT để giúp người chơi hình dung ra từ.

QUY TẮC BẮT BUỘC:
1. Viết câu gợi ý HOÀN TOÀN BẰNG TIẾNG VIỆT tự nhiên, dễ hiểu (Ví dụ: "Đây là một khái niệm liên quan đến...", "Từ này chỉ một đồ vật...").
2. Tối đa 2 câu ngắn (không quá 30 từ).
3. TUYỆT ĐỐI KHÔNG xuất hiện từ tiếng Anh "$answer" trong câu trả lời.
4. TUYỆT ĐỐI KHÔNG tiết lộ chữ cái hay cách đánh vần.
5. Chỉ trả về duy nhất đoạn văn gợi ý — không thêm lời mở đầu, lời chào hay nhãn phụ.
            """.trimIndent()
        } else {
            """
You are an AI assistant for WordMaster, a Wordle-style word puzzle game.
The SECRET WORD is: "$answer"

Current board state:
- Words the player guessed so far: $guessedWords
- Letters confirmed at correct positions: $confirmed
- Letters confirmed NOT in the word: $eliminated

YOUR TASK:
Write exactly ONE smart semantic hint about the secret word.

STRICT RULES (violating any rule is unacceptable):
1. Describe WHAT the word means or refers to: its category, field, or usage context.
2. Maximum 2 short sentences, 35 words total.
3. NEVER include the secret word "$answer" itself.
4. NEVER directly reveal any individual letter or spelling pattern of the answer.
5. Write in English only.
6. Output ONLY the hint text — no labels, no preamble, no extra commentary.
            """.trimIndent()
        }
    }

    // ── HTTP call via Ktor ────────────────────────────────────────────────────

    private suspend fun callApi(prompt: String): String {
        val body = """
        {
          "contents": [{"parts": [{"text": ${escapeJson(prompt)}}]}],
          "generationConfig": {
            "maxOutputTokens": 150,
            "temperature": 0.75
          }
        }
        """.trimIndent()

        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.bodyAsText()
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Extracts the first "text" field from the Gemini JSON response.
     * Uses simple regex parsing to avoid requiring kotlinx.serialization setup.
     * Returns the correct-language fallback if no text field is found
     * (e.g. when the API returns an error response).
     */
    private fun extractText(json: String, language: AppLanguage = AppLanguage.EN): String {
        val pattern = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.findAll(json).firstOrNull()
            ?.groupValues?.get(1)
            ?.replace("\\n", " ")
            ?.replace("\\\"", "\"")
            ?.trim()
            ?: if (language == AppLanguage.VI) FALLBACK_VI else FALLBACK
    }

    // ── Safety filter ─────────────────────────────────────────────────────────

    /**
     * Last-resort check: if the AI accidentally included the answer, swap in the correct-language fallback.
     */
    private fun sanitize(hint: String, answer: String, language: AppLanguage = AppLanguage.EN): String {
        val fallback = if (language == AppLanguage.VI) FALLBACK_VI else FALLBACK
        return if (hint.isBlank() || hint.lowercase().contains(answer.lowercase())) fallback
               else hint
    }

    // ── JSON escape ───────────────────────────────────────────────────────────

    private fun escapeJson(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"'  -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
