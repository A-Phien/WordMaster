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

//    private val model = "gemini-2.0-flash-lite"
      private val model = "gemini-flash-lite-latest"
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
     * Throws on any failure so callers can handle errors uniformly.
     */
    suspend fun generateSmartHint(
        answer: String,
        boardGuesses: List<List<String>>,
        keyStatus: Map<String, LetterStatus>,
        language: AppLanguage = AppLanguage.EN
    ): String {
        val prompt       = buildPrompt(answer, boardGuesses, keyStatus, language)
        val responseJson = callApi(prompt)
        println("[GeminiService] raw response (500c): ${responseJson.take(500)}")
        val hint = extractText(responseJson)
            ?: throw Exception("No 'text' field found in Gemini response")
        val safe = sanitize(hint, answer)
        if (safe == null) {
            println("[GeminiService] sanitize blocked hint (answer leaked). hint='${hint.take(80)}'")
            throw Exception("Sanitize blocked: answer appeared in hint")
        }
        return safe
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
TỪ BÍ MẬT CẦN ĐOÁN LÀ: "$answer" (một từ tiếng Anh 5 chữ cái).

Trạng thái bàn chơi:
- Từ đã đoán: $guessedWords
- Chữ cái đúng vị trí: $confirmed
- Chữ cái KHÔNG có trong từ: $eliminated

NHIỆM VỤ:
Viết một gợi ý CHI TIẾT NHẤT CÓ THỂ bằng Tiếng Việt, gần như "bật mí" đáp án để người chơi dễ đoán ra ngay.

YÊU CẦU CỤ THỂ — đề cập càng nhiều điểm dưới đây càng tốt:
- Nghĩa chính xác của từ (định nghĩa từ điển)
- Từ đồng nghĩa gần nhất trong tiếng Anh (ghi rõ "từ đồng nghĩa: ...")
- Ví dụ câu sử dụng từ này trong tiếng Anh
- Lĩnh vực / chủ đề (ví dụ: thể thao, nấu ăn, cảm xúc, thiên nhiên...)
- Từ trái nghĩa nếu có
- Bất kỳ liên tưởng nào giúp nhận ra từ

GIỚI HẠN:
- Tối đa 3 câu, viết hoàn toàn bằng Tiếng Việt tự nhiên.
- TUYỆT ĐỐI KHÔNG viết ra chính từ "$answer" hay cách đánh vần của nó.
- Chỉ trả về nội dung gợi ý, không thêm tiêu đề hay lời mở đầu.
            """.trimIndent()
        } else {
            """
You are an AI assistant for WordMaster, a Wordle-style word puzzle game.
The SECRET WORD is: "$answer"

Board state:
- Guessed words: $guessedWords
- Correct-position letters: $confirmed
- Eliminated letters: $eliminated

YOUR TASK:
Write a VERY DETAILED hint that nearly gives away the answer — this is a last-resort hint for a stuck player.

INCLUDE AS MANY OF THESE AS POSSIBLE:
- Precise dictionary definition of the word
- Its closest English synonyms (e.g. "Synonyms: ...")
- An example sentence using the word
- Its domain / topic (e.g. cooking, emotion, nature, sports...)
- Its antonym(s) if applicable
- Any strong association or cultural reference that would trigger recognition

CONSTRAINTS:
- Maximum 3 sentences, written in plain English.
- NEVER write the secret word "$answer" itself or spell it out.
- Output ONLY the hint text — no labels, no preamble.
            """.trimIndent()
        }
    }

    // ── HTTP call via Ktor ────────────────────────────────────────────────────

    private suspend fun callApi(prompt: String): String {
        val requestBody = """
        {
          "contents": [{"parts": [{"text": ${escapeJson(prompt)}}]}],
          "generationConfig": {
            "maxOutputTokens": 200,
            "temperature": 0.75
          }
        }
        """.trimIndent()

        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        val responseText = response.bodyAsText()
        // Throw on any non-2xx so the caller's catch block handles it correctly.
        // Without this check, Ktor silently returns error JSON (400/403/429)
        // and extractText falls through to FALLBACK without raising an exception.
        if (response.status.value !in 200..299) {
            throw Exception("Gemini HTTP ${response.status.value}: $responseText")
        }
        return responseText
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Extracts the first "text" field from the Gemini JSON response.
     * Returns null if no text field is found (caller decides how to handle).
     */
    private fun extractText(json: String): String? {
        val pattern = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.findAll(json).firstOrNull()
            ?.groupValues?.get(1)
            ?.replace("\\n", " ")
            ?.replace("\\\"", "\"")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    // ── Safety filter ─────────────────────────────────────────────────────────

    /**
     * Safety check: returns null if the hint reveals the answer as a standalone word.
     * Uses word-boundary matching to avoid false positives (e.g. answer "ARTHA"
     * legitimately appearing as a Sanskrit term in its own definition).
     * Returns null → caller throws → no penalty applied.
     */
    private fun sanitize(hint: String, answer: String): String? {
        if (hint.isBlank()) return null
        // Only block if the exact answer appears surrounded by word boundaries
        val pattern = Regex("""(?i)\b${Regex.escape(answer)}\b""")
        return if (pattern.containsMatchIn(hint)) null else hint
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
