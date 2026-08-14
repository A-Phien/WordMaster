package dev.johnoreilly.wordmaster.androidApp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.johnoreilly.wordmaster.shared.LetterStatus
import dev.johnoreilly.wordmaster.shared.WordMasterService
import kotlinx.coroutines.flow.first

// ── Singleton DataStore (one per process, separate file from StatsStore) ─────
private val Context.gameStateDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "word_master_game_state")

/**
 * Preferences DataStore wrapper for saving/restoring a mid-game [WordMasterService] state.
 *
 * Encoding contract
 * -----------------
 * - boardGuesses : rows joined by `|`, cells within a row joined by `,`
 *                  e.g. "A,P,P,L,E|,,,," for row-0=APPLE, row-1=empty
 * - boardStatus  : same shape, each cell is the [LetterStatus.ordinal] as a digit
 * - keyStatus    : "A:1,B:3,..." (letter:ordinal pairs, empty string if nothing typed yet)
 * - hintMessages : individual messages joined by [MSG_SEP] ("\u001F", ASCII unit-separator)
 */
class GameStateStore(context: Context) {

    private val store = context.applicationContext.gameStateDataStore

    // ── Keys ─────────────────────────────────────────────────────────────────
    private object Keys {
        /** Sentinel: true only when a PLAYING game has been persisted. */
        val IS_SAVED      = booleanPreferencesKey("is_saved")
        val ANSWER        = stringPreferencesKey("answer")
        val ATTEMPT       = intPreferencesKey("attempt")
        val BOARD_GUESSES = stringPreferencesKey("board_guesses")
        val BOARD_STATUS  = stringPreferencesKey("board_status")
        val KEY_STATUS    = stringPreferencesKey("key_status")
        val VOWEL_HINT    = booleanPreferencesKey("vowel_hint")
        val LETTER_HINT   = booleanPreferencesKey("letter_hint")
        val HINT_MESSAGES = stringPreferencesKey("hint_messages")
        val HINT_PENALTY  = intPreferencesKey("hint_penalty")
    }

    // ── Encoding helpers ──────────────────────────────────────────────────────

    private fun encodeBoard(board: ArrayList<ArrayList<String>>): String =
        board.joinToString("|") { row -> row.joinToString(",") }

    private fun decodeBoard(s: String): ArrayList<ArrayList<String>> {
        val rowCount = WordMasterService.MAX_NUMBER_OF_GUESSES
        val colCount = WordMasterService.NUMBER_LETTERS
        val rowParts = s.split("|")
        return ArrayList((0 until rowCount).map { i ->
            val cells = rowParts.getOrNull(i)?.split(",") ?: emptyList()
            ArrayList((0 until colCount).map { j -> cells.getOrNull(j) ?: "" })
        })
    }

    private fun encodeStatus(board: ArrayList<ArrayList<LetterStatus>>): String =
        board.joinToString("|") { row ->
            row.joinToString(",") { it.ordinal.toString() }
        }

    private fun decodeStatus(s: String): ArrayList<ArrayList<LetterStatus>> {
        val allStatuses = LetterStatus.entries          // List<LetterStatus> (Kotlin 1.9+)
        val rowCount = WordMasterService.MAX_NUMBER_OF_GUESSES
        val colCount = WordMasterService.NUMBER_LETTERS
        val rowParts = s.split("|")
        return ArrayList((0 until rowCount).map { i ->
            val cells = rowParts.getOrNull(i)?.split(",") ?: emptyList()
            ArrayList((0 until colCount).map { j ->
                val ord = cells.getOrNull(j)?.toIntOrNull() ?: 0
                allStatuses.getOrElse(ord) { LetterStatus.UNGUESSED }
            })
        })
    }

    private fun encodeKeyStatus(map: Map<String, LetterStatus>): String =
        map.entries.joinToString(",") { (k, v) -> "$k:${v.ordinal}" }

    private fun decodeKeyStatus(s: String): Map<String, LetterStatus> {
        if (s.isBlank()) return emptyMap()
        val allStatuses = LetterStatus.entries
        return s.split(",").mapNotNull { pair ->
            val idx = pair.indexOf(':')
            if (idx < 0) return@mapNotNull null
            val letter = pair.substring(0, idx)
            val ord = pair.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
            letter to allStatuses.getOrElse(ord) { LetterStatus.UNGUESSED }
        }.toMap()
    }

    /** ASCII Unit Separator — safe delimiter that never appears in hint text. */
    private val MSG_SEP = "\u001F"

    private fun encodeMessages(msgs: List<String>): String = msgs.joinToString(MSG_SEP)
    private fun decodeMessages(s: String): List<String> =
        if (s.isBlank()) emptyList() else s.split(MSG_SEP).filter { it.isNotEmpty() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Persist the full in-progress game state from [service].
     * Should only be called while [WordMasterService.gameStatus] == PLAYING.
     */
    suspend fun saveGame(service: WordMasterService) {
        store.edit { prefs ->
            prefs[Keys.IS_SAVED]      = true
            prefs[Keys.ANSWER]        = service.answer
            prefs[Keys.ATTEMPT]       = service.currentGuessAttempt
            prefs[Keys.BOARD_GUESSES] = encodeBoard(service.boardGuesses.value)
            prefs[Keys.BOARD_STATUS]  = encodeStatus(service.boardStatus.value)
            prefs[Keys.KEY_STATUS]    = encodeKeyStatus(service.keyStatus.value)
            prefs[Keys.VOWEL_HINT]    = service.vowelHintUsed.value
            prefs[Keys.LETTER_HINT]   = service.letterHintUsed.value
            prefs[Keys.HINT_MESSAGES] = encodeMessages(service.hintMessages.value)
            prefs[Keys.HINT_PENALTY]  = service.exportHintPenalty()
        }
    }

    /**
     * Read the persisted game state and restore it into [service].
     *
     * @return `true` if a saved PLAYING game was found and restored, `false` otherwise.
     */
    suspend fun restoreGame(service: WordMasterService): Boolean {
        val prefs = store.data.first()
        if (prefs[Keys.IS_SAVED] != true) return false

        val answer        = prefs[Keys.ANSWER]        ?: return false
        val attempt       = prefs[Keys.ATTEMPT]       ?: 0
        val boardGuesses  = prefs[Keys.BOARD_GUESSES] ?: return false
        val boardStatus   = prefs[Keys.BOARD_STATUS]  ?: return false

        service.restoreGameState(
            savedAnswer          = answer,
            savedAttempt         = attempt,
            savedBoardGuesses    = decodeBoard(boardGuesses),
            savedBoardStatus     = decodeStatus(boardStatus),
            savedKeyStatus       = decodeKeyStatus(prefs[Keys.KEY_STATUS] ?: ""),
            savedVowelHintUsed   = prefs[Keys.VOWEL_HINT]    ?: false,
            savedLetterHintUsed  = prefs[Keys.LETTER_HINT]   ?: false,
            savedHintMessages    = decodeMessages(prefs[Keys.HINT_MESSAGES] ?: ""),
            savedHintPenalty     = prefs[Keys.HINT_PENALTY]  ?: 0
        )
        return true
    }

    /**
     * Erase the persisted game state.
     * Call this when the game ends (WON/LOST), on New Game, or on explicit reset.
     */
    suspend fun clearGame() {
        store.edit { prefs: MutablePreferences ->
            prefs[Keys.IS_SAVED] = false
            prefs.remove(Keys.ANSWER)
            prefs.remove(Keys.ATTEMPT)
            prefs.remove(Keys.BOARD_GUESSES)
            prefs.remove(Keys.BOARD_STATUS)
            prefs.remove(Keys.KEY_STATUS)
            prefs.remove(Keys.VOWEL_HINT)
            prefs.remove(Keys.LETTER_HINT)
            prefs.remove(Keys.HINT_MESSAGES)
            prefs.remove(Keys.HINT_PENALTY)
        }
    }
}
