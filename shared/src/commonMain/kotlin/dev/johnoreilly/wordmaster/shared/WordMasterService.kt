package dev.johnoreilly.wordmaster.shared

import com.rickclephas.kmp.nativecoroutines.NativeCoroutineScope
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import kotlinx.coroutines.flow.MutableStateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import dev.johnoreilly.wordmaster.shared.LetterStatus.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okio.SYSTEM


enum class LetterStatus {
    UNGUESSED, CORRECT_POSITION, INCORRECT_POSITION, NOT_IN_WORD
}

enum class GameStatus {
    PLAYING, WON, LOST
}

data class GameStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val totalScore: Int = 0,
    val guessDistribution: Map<Int, Int> = emptyMap()
) {
    val winRate: Int
        get() = if (gamesPlayed == 0) 0 else (wins * 100) / gamesPlayed
}


class WordMasterService(
    wordsFilePath: String,
    geminiApiKey: String = ""
) {
    @NativeCoroutineScope
    val coroutineScope: CoroutineScope = MainScope()

    /** Gemini AI service for Smart Hint (null if no API key provided). */
    private val geminiService: GeminiService? =
        if (geminiApiKey.isNotBlank()) GeminiService(geminiApiKey) else null

    /** Validates guesses online when the word is not in the local dictionary. */
    private val onlineValidator = OnlineWordValidator()

    private val validWords = mutableListOf<String>()
    private val validWordsSet = mutableSetOf<String>()

    var answer = ""
    var currentGuessAttempt = 0

    @NativeCoroutines
    val boardGuesses: MutableStateFlow<ArrayList<ArrayList<String>>> = MutableStateFlow(arrayListOf())

    @NativeCoroutines
    val boardStatus: MutableStateFlow<ArrayList<ArrayList<LetterStatus>>> = MutableStateFlow(arrayListOf())

    @NativeCoroutines
    val revealedAnswer: MutableStateFlow<String?> = MutableStateFlow(null)

    @NativeCoroutines
    val lastGuessCorrect: MutableStateFlow<Boolean> = MutableStateFlow(false)

    @NativeCoroutines
    val gameStatus: MutableStateFlow<GameStatus> = MutableStateFlow(GameStatus.PLAYING)

    @NativeCoroutines
    val currentScore: MutableStateFlow<Int> = MutableStateFlow(0)

    @NativeCoroutines
    val gameStats: MutableStateFlow<GameStats> = MutableStateFlow(GameStats())

    /** Current display language; drives all UI strings and AI hint language. */
    @NativeCoroutines
    val appLanguage: MutableStateFlow<AppLanguage> = MutableStateFlow(AppLanguage.EN)

    /** Change the display language. UI rebuilds automatically via StateFlow. */
    fun setLanguage(lang: AppLanguage) { appLanguage.value = lang }

    // Best-known status for each letter typed so far, used to colour the on-screen keyboard.
    @NativeCoroutines
    val keyStatus: MutableStateFlow<Map<String, LetterStatus>> = MutableStateFlow(emptyMap())

    // Transient message shown to the user when a guess is rejected (e.g. too short / not a word).
    @NativeCoroutines
    val guessError: MutableStateFlow<String?> = MutableStateFlow(null)

    // ── Hint system ──────────────────────────────────────────────────────────
    @NativeCoroutines
    val vowelHintUsed: MutableStateFlow<Boolean> = MutableStateFlow(false)

    @NativeCoroutines
    val letterHintUsed: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** Accumulated hint messages shown on the GameScreen. */
    @NativeCoroutines
    val hintMessages: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    /** True after the AI Smart Hint has been used this game. */
    @NativeCoroutines
    val aiHintUsed: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** True while the Gemini API call is in progress. */
    @NativeCoroutines
    val isAiThinking: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** True while an online word validation request is in progress. */
    @NativeCoroutines
    val isValidatingWord: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** Score penalty accumulated through hint usage; applied on win. */
    private var hintPenalty: Int = 0
    // ─────────────────────────────────────────────────────────────────────────


    init {
        println("wordsFilePath = $wordsFilePath")
        readWords(wordsFilePath.toPath())
        resetGame()
    }

    fun resetGame() {
        currentGuessAttempt = 0
        answer = validWords.random().uppercase()
        revealedAnswer.value = null
        lastGuessCorrect.value = false
        gameStatus.value = GameStatus.PLAYING
        currentScore.value = 0
        keyStatus.value = emptyMap()
        guessError.value = null

        // Reset hint state
        vowelHintUsed.value = false
        letterHintUsed.value = false
        aiHintUsed.value = false
        hintMessages.value = emptyList()
        hintPenalty = 0

        // set default values for guesses/letter status info
        val newBoardStatus = arrayListOf<ArrayList<LetterStatus>>()
        val newBoardGuesses = arrayListOf<ArrayList<String>>()

        for (guessAttempt in 0 until MAX_NUMBER_OF_GUESSES) {
            val statusList = arrayListOf<LetterStatus>()
            val guesses = arrayListOf<String>()
            for (character in 0 until NUMBER_LETTERS) {
                statusList.add(UNGUESSED)
                guesses.add("")
            }
            newBoardStatus.add(statusList)
            newBoardGuesses.add(guesses)
        }
        boardStatus.value = newBoardStatus
        boardGuesses.value = newBoardGuesses
    }

    private fun isGameFinished(): Boolean =
        lastGuessCorrect.value || currentGuessAttempt >= MAX_NUMBER_OF_GUESSES

    fun isValidWord(word: String): Boolean = validWordsSet.contains(word.uppercase())

    // Append a letter to the next empty cell of the current guess row.
    fun addLetter(letter: String) {
        if (isGameFinished()) return
        val character = letter.uppercase().take(1)
        if (character.isEmpty()) return

        val row = boardGuesses.value[currentGuessAttempt]
        val column = row.indexOfFirst { it.isEmpty() }
        if (column == -1) return

        setGuess(currentGuessAttempt, column, character)
    }

    // Clear the last filled cell of the current guess row.
    fun removeLetter() {
        if (isGameFinished()) return

        val row = boardGuesses.value[currentGuessAttempt]
        val column = row.indexOfLast { it.isNotEmpty() }
        if (column == -1) return

        setGuess(currentGuessAttempt, column, "")
    }

    fun clearGuessError() {
        guessError.value = null
    }

    fun revealAnswerForDebug() {
        revealedAnswer.value = answer
    }

    fun fillAnswerForDebug() {
        if (isGameFinished()) return
        setCurrentGuess(answer)
    }

    fun winNowForDebug() {
        if (isGameFinished()) return
        setCurrentGuess(answer)
        checkGuess()
    }

    fun loseNowForDebug() {
        if (isGameFinished()) return

        val losingWord = validWords
            .map { it.uppercase() }
            .firstOrNull { it.length == NUMBER_LETTERS && it != answer }
            ?: return

        while (!isGameFinished()) {
            setCurrentGuess(losingWord)
            checkGuess()
        }
    }

    fun resetStatsForDebug() {
        gameStats.value = GameStats()
        currentScore.value = 0
    }

    /**
     * Restore previously persisted stats (e.g. from Android DataStore or iOS UserDefaults).
     * Does NOT touch any other game state.
     */
    fun restoreStats(stats: GameStats) {
        gameStats.value = stats
    }

    /**
     * Restore a full mid-game state that was previously persisted (Android DataStore, etc.).
     * Overwrites the current in-memory state completely and forces the game back to PLAYING.
     * Must only be called after [init] has finished (words loaded + resetGame() called).
     */
    fun restoreGameState(
        savedAnswer: String,
        savedAttempt: Int,
        savedBoardGuesses: ArrayList<ArrayList<String>>,
        savedBoardStatus: ArrayList<ArrayList<LetterStatus>>,
        savedKeyStatus: Map<String, LetterStatus>,
        savedVowelHintUsed: Boolean,
        savedLetterHintUsed: Boolean,
        savedHintMessages: List<String>,
        savedHintPenalty: Int
    ) {
        answer = savedAnswer
        currentGuessAttempt = savedAttempt
        boardGuesses.value = savedBoardGuesses
        boardStatus.value = savedBoardStatus
        keyStatus.value = savedKeyStatus
        vowelHintUsed.value = savedVowelHintUsed
        letterHintUsed.value = savedLetterHintUsed
        hintMessages.value = savedHintMessages
        hintPenalty = savedHintPenalty
        // Ensure consistent PLAYING state
        gameStatus.value = GameStatus.PLAYING
        revealedAnswer.value = null
        lastGuessCorrect.value = false
        currentScore.value = 0
        guessError.value = null
    }

    /** Expose the private hint penalty so Android can persist it. */
    fun exportHintPenalty(): Int = hintPenalty

    // ── Hint functions ────────────────────────────────────────────────────────

    /**
     * Hint 1 – Vowel Count.
     * Reveals how many vowels (A/E/I/O/U) are in the answer. Costs 50 pts.
     */
    fun useVowelHint() {
        if (vowelHintUsed.value || gameStatus.value != GameStatus.PLAYING) return
        val vowelCount = answer.count { it in setOf('A', 'E', 'I', 'O', 'U') }
        val msg = if (appLanguage.value == AppLanguage.VI) {
            val unit = if (vowelCount != 1) "nguyên âm" else "nguyên âm"
            "Số nguyên âm: $vowelCount $unit trong đáp án (-50 pts)"
        } else {
            val plural = if (vowelCount != 1) "vowels" else "vowel"
            "Vowel count: $vowelCount $plural in the answer (-50 pts)"
        }
        hintMessages.value = hintMessages.value + msg
        vowelHintUsed.value = true
        hintPenalty += 50
    }

    /**
     * Hint 2 – Reveal a Letter.
     * Reveals one correctly-positioned letter the player hasn't yet discovered. Costs 100 pts.
     */
    fun useRevealLetterHint() {
        if (letterHintUsed.value || gameStatus.value != GameStatus.PLAYING) return
        // Positions already confirmed CORRECT_POSITION in any completed row
        val alreadyCorrect = (0 until currentGuessAttempt)
            .flatMap { row ->
                boardStatus.value[row]
                    .mapIndexed { col, s -> if (s == CORRECT_POSITION) col else -1 }
                    .filter { it >= 0 }
            }
            .toSet()
        val candidates = (0 until NUMBER_LETTERS).filter { it !in alreadyCorrect }
        if (candidates.isEmpty()) {
            val allKnownMsg = if (appLanguage.value == AppLanguage.VI)
                "Tất cả vị trí đã được biết."
            else
                "All positions are already known."
            hintMessages.value = hintMessages.value + allKnownMsg
            return
        }

        val pos = candidates.random()
        val msg = if (appLanguage.value == AppLanguage.VI)
            "Vị trí ${pos + 1} là '${answer[pos]}' (-100 pts)"
        else
            "Position ${pos + 1} is '${answer[pos]}' (-100 pts)"
        hintMessages.value = hintMessages.value + msg
        letterHintUsed.value = true
        hintPenalty += 100
    }

    /**
     * Hint 3 – AI Smart Hint (self-contained).
     * Calls [GeminiService] internally, manages [isAiThinking] state,
     * and applies the −150 pts penalty. Safe to call from any platform UI.
     */
    suspend fun requestAiSmartHint() {
        if (aiHintUsed.value || gameStatus.value != GameStatus.PLAYING) return
        val lang = appLanguage.value
        val service = geminiService ?: run {
            val msg = if (lang == AppLanguage.VI)
                "🤖 AI chưa được cấu hình. Vui lòng cung cấp API key.  (−0 pts)"
            else
                "🤖 AI is not configured. Please provide an API key.  (−0 pts)"
            hintMessages.value = hintMessages.value + msg
            return
        }

        isAiThinking.value = true
        try {
            val submittedGuesses = boardGuesses.value.take(currentGuessAttempt)
            val hint = service.generateSmartHint(
                answer       = answer,
                boardGuesses = submittedGuesses,
                keyStatus    = keyStatus.value,
                language     = lang
            )
            hintMessages.value = hintMessages.value + "🤖 $hint  (−150 pts)"
            aiHintUsed.value = true
            hintPenalty += 150
        } catch (_: Exception) {
            val fallback = if (lang == AppLanguage.VI) GeminiService.FALLBACK_VI else GeminiService.FALLBACK
            hintMessages.value = hintMessages.value + "🤖 $fallback  (−0 pts)"
        } finally {
            isAiThinking.value = false
        }
    }

    /**
     * Direct injection for AI hint text (used by Android GameStateStore restore).
     * Prefer [requestAiSmartHint] for normal gameplay.
     */
    fun useAiSmartHint(aiMessage: String) {
        if (aiHintUsed.value || gameStatus.value != GameStatus.PLAYING) return
        hintMessages.value = hintMessages.value + "🤖 $aiMessage  (−150 pts)"
        aiHintUsed.value = true
        hintPenalty += 150
    }
    // ─────────────────────────────────────────────────────────────────────────

    // Validate the current row and, if it's a legal word, evaluate it.
    // Synchronous version — only checks local dictionary.
    fun submitGuess() {
        if (isGameFinished()) return

        val currentGuess = boardGuesses.value[currentGuessAttempt].joinToString("")
        if (currentGuess.length < NUMBER_LETTERS) {
            guessError.value = AppStrings(appLanguage.value).notEnoughLetters
            return
        }
        if (!isValidWord(currentGuess)) {
            guessError.value = AppStrings(appLanguage.value).notInWordList
            return
        }
        guessError.value = null
        checkGuess()
    }

    /**
     * Async version of [submitGuess] that falls back to online dictionary validation
     * when the guessed word is not found in the local words.txt.
     *
     * Flow:
     *   1. Check local list → accept immediately (fast path, works offline)
     *   2. If not local AND network is available → ask Free Dictionary API
     *      - API 200 → add to local cache + accept
     *      - API 4xx/offline → show "not in word list" error
     */
    suspend fun submitGuessAsync() {
        if (isGameFinished()) return

        val currentGuess = boardGuesses.value[currentGuessAttempt].joinToString("")
        if (currentGuess.length < NUMBER_LETTERS) {
            guessError.value = AppStrings(appLanguage.value).notEnoughLetters
            return
        }

        if (isValidWord(currentGuess)) {
            // Fast path: found in local dictionary
            guessError.value = null
            checkGuess()
            return
        }

        // Slow path: not in local list → check online
        isValidatingWord.value = true
        try {
            val validOnline = onlineValidator.isValidWord(currentGuess)
            if (validOnline) {
                // Cache for remainder of this session so repeated guesses are instant
                addToLocalCache(currentGuess)
                guessError.value = null
                checkGuess()
            } else {
                guessError.value = AppStrings(appLanguage.value).notInWordList
            }
        } finally {
            isValidatingWord.value = false
        }
    }

    /** Adds a word that was validated online to the local session cache. */
    private fun addToLocalCache(word: String) {
        val upper = word.uppercase()
        if (!validWordsSet.contains(upper)) {
            validWords.add(upper)
            validWordsSet.add(upper)
        }
    }

    fun setGuess(guessAttempt: Int, character: Int, guess: String) {
        // need to make deep copy to trigger MutableStateFlow to emit update
        val currentBoardGuesses = boardGuesses.value

        val newBoardGuesses = arrayListOf<ArrayList<String>>()
        for (guessAttempt in 0 until MAX_NUMBER_OF_GUESSES) {
            val guesses = ArrayList(currentBoardGuesses[guessAttempt])
            newBoardGuesses.add(guesses)
        }

        newBoardGuesses[guessAttempt][character] = guess
        boardGuesses.value = newBoardGuesses
    }

    private fun setCurrentGuess(word: String) {
        val letters = word.uppercase().take(NUMBER_LETTERS)
        for (index in 0 until NUMBER_LETTERS) {
            setGuess(currentGuessAttempt, index, letters.getOrNull(index)?.toString() ?: "")
        }
    }

    fun checkGuess() {
        val currentGuess = boardGuesses.value[currentGuessAttempt].joinToString("")
        if (currentGuess.length == NUMBER_LETTERS) {
            val status = checkWord(currentGuess)

            val currentStatusCopy = ArrayList(boardStatus.value)
            currentStatusCopy[currentGuessAttempt] = status
            boardStatus.value = currentStatusCopy

            updateKeyStatus(currentGuess, status)

            val isCorrect = status.all { it == CORRECT_POSITION }
            if ( isCorrect ) {
                lastGuessCorrect.value = true
                gameStatus.value = GameStatus.WON
                val guessesUsed = currentGuessAttempt + 1
                val score = maxOf(0, scoreForGuess(guessesUsed) - hintPenalty)
                currentScore.value = score
                recordWin(guessesUsed, score)
            }
            currentGuessAttempt++

            // Reveal the answer if all guesses are completed and the word wasn't guessed
            if (!isCorrect && currentGuessAttempt >= MAX_NUMBER_OF_GUESSES) {
                revealedAnswer.value = answer
                gameStatus.value = GameStatus.LOST
                recordLoss()
            }
        }
    }

    private fun scoreForGuess(guessesUsed: Int): Int =
        (MAX_NUMBER_OF_GUESSES - guessesUsed + 1) * 100

    private fun recordWin(guessesUsed: Int, score: Int) {
        val stats = gameStats.value
        val newStreak = stats.currentStreak + 1
        val newDistribution = stats.guessDistribution.toMutableMap()
        newDistribution[guessesUsed] = (newDistribution[guessesUsed] ?: 0) + 1

        gameStats.value = stats.copy(
            gamesPlayed = stats.gamesPlayed + 1,
            wins = stats.wins + 1,
            currentStreak = newStreak,
            maxStreak = maxOf(stats.maxStreak, newStreak),
            totalScore = stats.totalScore + score,
            guessDistribution = newDistribution
        )
    }

    private fun recordLoss() {
        val stats = gameStats.value
        gameStats.value = stats.copy(
            gamesPlayed = stats.gamesPlayed + 1,
            losses = stats.losses + 1,
            currentStreak = 0
        )
    }

    // Merge the latest guess result into the per-letter keyboard status, only ever upgrading
    // (NOT_IN_WORD -> INCORRECT_POSITION -> CORRECT_POSITION).
    private fun updateKeyStatus(guess: String, status: ArrayList<LetterStatus>) {
        val newKeyStatus = keyStatus.value.toMutableMap()
        guess.forEachIndexed { index, char ->
            val letter = char.toString()
            val newStatus = status[index]
            if (statusRank(newStatus) > statusRank(newKeyStatus[letter])) {
                newKeyStatus[letter] = newStatus
            }
        }
        keyStatus.value = newKeyStatus
    }

    private fun statusRank(status: LetterStatus?): Int = when (status) {
        CORRECT_POSITION -> 3
        INCORRECT_POSITION -> 2
        NOT_IN_WORD -> 1
        else -> 0
    }

    private fun checkWord(guess: String): ArrayList<LetterStatus> {
        val letterStatusList = arrayListOf(NOT_IN_WORD, NOT_IN_WORD, NOT_IN_WORD, NOT_IN_WORD, NOT_IN_WORD)

        val unusedAnswerLetters = answer.toMutableList()

        // check correct positions
        for (index in 0 until NUMBER_LETTERS) {
            val letter = guess[index]
            if (letter == answer[index]) {
                letterStatusList[index] = CORRECT_POSITION
                unusedAnswerLetters.remove(letter)
            }
        }

        // check letters in incorrect position
        for (index in 0 until NUMBER_LETTERS) {
            if (letterStatusList[index] == CORRECT_POSITION) continue

            val letter = guess[index]
            if (letter in unusedAnswerLetters) {
                letterStatusList[index] = INCORRECT_POSITION
                unusedAnswerLetters.remove(letter)
            }
        }

        return letterStatusList
    }


    private fun readWords(path: Path) {
        // an error will be shown in IDE for now until https://github.com/square/okio/pull/980
        // is resolved....but will build/run ok
        FileSystem.SYSTEM.read(path) {
            while (true) {
                val word = this.readUtf8Line() ?: break
                validWords.add(word)
                validWordsSet.add(word.uppercase())
            }
        }
    }

    companion object {
        const val NUMBER_LETTERS = 5
        const val MAX_NUMBER_OF_GUESSES = 6
    }
}
