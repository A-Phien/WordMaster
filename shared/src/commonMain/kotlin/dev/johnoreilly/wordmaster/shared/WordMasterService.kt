package dev.johnoreilly.wordmaster.shared

import com.rickclephas.kmp.nativecoroutines.NativeCoroutineScope
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import dev.johnoreilly.wordmaster.shared.LetterStatus.*
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
    private val wordsDir: String,
    geminiApiKey: String = ""
) {
    @NativeCoroutineScope
    val coroutineScope: CoroutineScope = MainScope()

    /** Gemini AI service for Smart Hint (null if no API key provided). */
    private val geminiService: GeminiService? =
        if (geminiApiKey.isNotBlank()) GeminiService(geminiApiKey) else null

    /** Validates guesses online when the word is not in the local dictionary. */
    private val onlineValidator = OnlineWordValidator()

    /** All word lists, keyed by word length. Populated once at startup on IO thread. */
    private val allWordLists = mutableMapOf<Int, List<String>>()
    /** All word sets for fast validation, keyed by word length. */
    private val allWordSets  = mutableMapOf<Int, Set<String>>()

    /** Curated concrete noun lists with clear visual representations, keyed by length. */
    private val allTargetLists = mutableMapOf<Int, List<String>>()

    /** Active word list (backed by the preloaded list for the current length). */
    private var validWords: MutableList<String> = mutableListOf()
    private var validWordsSet: MutableSet<String> = mutableSetOf()

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

    /** Current word length (3, 4, 5, or 6). Drives board size and active word list. */
    @NativeCoroutines
    val wordLength: MutableStateFlow<Int> = MutableStateFlow(DEFAULT_WORD_LENGTH)

    /** True only during initial startup while all word lists are being loaded. */
    @NativeCoroutines
    val isLoadingWords: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Switch word length instantly — NO file I/O.
     * All word lists are already preloaded in [allWordLists].
     */
    fun setWordLength(length: Int) {
        if (length == wordLength.value) return
        val list = allWordLists[length]
        if (list == null || list.isEmpty()) return   // not loaded yet (shouldn't happen)
        wordLength.value = length
        validWords    = list.toMutableList()
        validWordsSet = (allWordSets[length] ?: emptySet()).toMutableSet()
        resetGame()
    }

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
        // Preload ALL word lists on IO thread — once at startup.
        // After this, switching word length is instant (no more disk I/O).
        isLoadingWords.value = true
        coroutineScope.launch(Dispatchers.IO) {
            for (length in SUPPORTED_LENGTHS) {
                val fileName = if (length == DEFAULT_WORD_LENGTH) "words.txt" else "words_$length.txt"
                val path = "$wordsDir/$fileName".toPath()
                val list = mutableListOf<String>()
                val set  = mutableSetOf<String>()
                try {
                    FileSystem.SYSTEM.read(path) {
                        while (true) {
                            val word = readUtf8Line() ?: break
                            if (word.isNotBlank()) {
                                list.add(word.trim())
                                set.add(word.trim().uppercase())
                            }
                        }
                    }
                } catch (_: Exception) {
                    // File not available yet — skip silently
                }

                // Read target words (curated concrete nouns with clear images)
                val targetFileName = "targets_$length.txt"
                val targetPath = "$wordsDir/$targetFileName".toPath()
                val targetList = mutableListOf<String>()
                try {
                    FileSystem.SYSTEM.read(targetPath) {
                        while (true) {
                            val word = readUtf8Line() ?: break
                            val trimmed = word.trim()
                            if (trimmed.isNotBlank()) {
                                targetList.add(trimmed)
                                set.add(trimmed.uppercase())
                            }
                        }
                    }
                } catch (_: Exception) {}

                allWordLists[length] = list
                allWordSets[length]  = set
                allTargetLists[length] = targetList
            }

            withContext(Dispatchers.Main) {
                // Point to the default (5-letter) list
                validWords    = (allWordLists[DEFAULT_WORD_LENGTH] ?: emptyList()).toMutableList()
                validWordsSet = (allWordSets[DEFAULT_WORD_LENGTH]  ?: emptySet()).toMutableSet()
                isLoadingWords.value = false
                resetGame()
            }
        }
    }

    fun resetGame() {
        currentGuessAttempt = 0
        val targets = allTargetLists[wordLength.value]
        answer = if (!targets.isNullOrEmpty()) {
            targets.random().uppercase()
        } else {
            validWords.random().uppercase()
        }
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
            for (character in 0 until wordLength.value) {
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
        lastGuessCorrect.value || currentGuessAttempt >= MAX_NUMBER_OF_GUESSES || gameStatus.value != GameStatus.PLAYING

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

    /**
     * Called when the per-guess countdown timer reaches zero.
     * Immediately ends the game as a loss — no extra attempts consumed.
     */
    fun timeoutGuess() {
        if (isGameFinished()) return
        revealedAnswer.value = answer
        gameStatus.value = GameStatus.LOST
        recordLoss()
    }

    /**
     * Called when the player deliberately taps "Reveal Answer".
     * Immediately reveals the answer and records a loss.
     */
    fun revealAndLose() {
        if (isGameFinished()) return
        revealedAnswer.value = answer
        gameStatus.value = GameStatus.LOST
        recordLoss()
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
            .firstOrNull { it.length == wordLength.value && it != answer }
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
        val candidates = (0 until wordLength.value).filter { it !in alreadyCorrect }
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
        // Mark used immediately so button disables while thinking
        aiHintUsed.value = true
        try {
            val submittedGuesses = boardGuesses.value.take(currentGuessAttempt)
            val hint = service.generateSmartHint(
                answer       = answer,
                boardGuesses = submittedGuesses,
                keyStatus    = keyStatus.value,
                language     = lang
            )
            // ✅ API succeeded — charge penalty and show real hint
            hintMessages.value = hintMessages.value + "🤖 $hint  (−150 pts)"
            hintPenalty += 150
        } catch (e: Exception) {
            // ❌ API failed (wrong key, quota exceeded, offline) — NO penalty, allow retry
            println("[AI Hint] error: ${e.message}")
            aiHintUsed.value = false   // re-enable so player can try again later
            val errMsg = if (lang == AppLanguage.VI)
                "🤖 Không thể kết nối AI. Kiểm tra API key hoặc mạng. (−0 pts)"
            else
                "🤖 Could not reach AI. Check API key or network. (−0 pts)"
            hintMessages.value = hintMessages.value + errMsg
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
        if (currentGuess.length < wordLength.value) {
            guessError.value = AppStrings(appLanguage.value).notEnoughLetters(wordLength.value)
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
        if (currentGuess.length < wordLength.value) {
            guessError.value = AppStrings(appLanguage.value).notEnoughLetters(wordLength.value)
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
        val letters = word.uppercase().take(wordLength.value)
        for (index in 0 until wordLength.value) {
            setGuess(currentGuessAttempt, index, letters.getOrNull(index)?.toString() ?: "")
        }
    }

    fun checkGuess() {
        val currentGuess = boardGuesses.value[currentGuessAttempt].joinToString("")
        if (currentGuess.length == wordLength.value) {
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
        val letterStatusList = ArrayList(List(wordLength.value) { NOT_IN_WORD })

        val unusedAnswerLetters = answer.toMutableList()

        // check correct positions
        for (index in 0 until wordLength.value) {
            val letter = guess[index]
            if (letter == answer[index]) {
                letterStatusList[index] = CORRECT_POSITION
                unusedAnswerLetters.remove(letter)
            }
        }

        // check letters in incorrect position
        for (index in 0 until wordLength.value) {
            if (letterStatusList[index] == CORRECT_POSITION) continue

            val letter = guess[index]
            if (letter in unusedAnswerLetters) {
                letterStatusList[index] = INCORRECT_POSITION
                unusedAnswerLetters.remove(letter)
            }
        }


        return letterStatusList
    }

    companion object {
        /** Default word length (5-letter Wordle). */
        const val DEFAULT_WORD_LENGTH = 5
        const val MAX_NUMBER_OF_GUESSES = 6

        /** Supported word lengths players can choose from. */
        val SUPPORTED_LENGTHS = listOf(3, 4, 5, 6)
    }
}
