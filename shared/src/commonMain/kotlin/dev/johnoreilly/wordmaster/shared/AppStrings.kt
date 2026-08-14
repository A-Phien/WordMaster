package dev.johnoreilly.wordmaster.shared

/**
 * Supported display languages for the app UI.
 * The game board itself (letters A-Z) is always in English.
 */
enum class AppLanguage { EN, VI }

/**
 * All user-facing strings for the app, available in English and Vietnamese.
 * Pass the [AppStrings] instance down through the Compose tree from the root.
 *
 * Usage: val s = strings  // from LocalAppStrings.current
 *        Text(s.playNow)
 */
data class AppStrings(val language: AppLanguage) {

    // ── Navigation / TopAppBar ────────────────────────────────────────────────
    val back          get() = if (language == AppLanguage.VI) "Quay lại"  else "Back"
    val gameTitle     get() = if (language == AppLanguage.VI) "Trò chơi"  else "Game"
    val statsTitle    get() = if (language == AppLanguage.VI) "Thống kê"  else "Statistics"
    val settingsTitle get() = if (language == AppLanguage.VI) "Cài đặt"   else "Settings"

    // ── HomeScreen ────────────────────────────────────────────────────────────
    val guessHiddenWord get() = if (language == AppLanguage.VI) "Đoán từ bí mật"  else "Guess the hidden word"
    val playNow         get() = if (language == AppLanguage.VI) "Chơi Ngay"        else "Play Now"
    val stats           get() = if (language == AppLanguage.VI) "Thống kê"         else "Stats"
    val settings        get() = if (language == AppLanguage.VI) "Cài đặt"          else "Settings"

    // ── Stat labels (HomeScreen + StatsScreen) ────────────────────────────────
    val played         get() = if (language == AppLanguage.VI) "Đã chơi"         else "Played"
    val winRate        get() = if (language == AppLanguage.VI) "Tỉ lệ thắng"     else "Win %"
    val streak         get() = if (language == AppLanguage.VI) "Chuỗi thắng"     else "Streak"
    val wins           get() = if (language == AppLanguage.VI) "Chiến thắng"     else "Wins"
    val losses         get() = if (language == AppLanguage.VI) "Thua"            else "Losses"
    val currentStreak  get() = if (language == AppLanguage.VI) "Chuỗi hiện tại"  else "Current streak"
    val maxStreak      get() = if (language == AppLanguage.VI) "Chuỗi tốt nhất"  else "Max streak"
    val totalScore     get() = if (language == AppLanguage.VI) "Tổng điểm"       else "Total score"
    val guessDist      get() = if (language == AppLanguage.VI) "Phân bổ lượt đoán" else "Guess distribution"

    // ── GameScreen ────────────────────────────────────────────────────────────
    val score    get() = if (language == AppLanguage.VI) "Điểm số"     else "Score"
    val newGame  get() = if (language == AppLanguage.VI) "Ván mới"     else "New Game"
    val viewStats get() = if (language == AppLanguage.VI) "Xem thống kê" else "Stats"
    val answerLabel get() = if (language == AppLanguage.VI) "Đáp án: "   else "Answer: "
    val devTools get() = if (language == AppLanguage.VI) "Dev Tools"   else "Dev Tools"
    val hideDevTools get() = if (language == AppLanguage.VI) "Ẩn Dev Tools" else "Hide Dev Tools"

    // ── Guess errors (also used in shared WordMasterService) ──────────────────
    val notEnoughLetters get() = if (language == AppLanguage.VI) "Chưa đủ 5 chữ cái"        else "Not enough letters"
    val notInWordList    get() = if (language == AppLanguage.VI) "Từ không có trong từ điển" else "Not in word list"

    // ── ResultSheet ───────────────────────────────────────────────────────────
    val victory       get() = if (language == AppLanguage.VI) "Chiến Thắng!" else "Victory"
    val wordRevealed  get() = if (language == AppLanguage.VI) "Từ bị lộ"     else "Word Revealed"
    val guesses       get() = if (language == AppLanguage.VI) "Lượt đoán"    else "Guesses"
    val playAgain     get() = if (language == AppLanguage.VI) "Chơi lại"     else "Play Again"
    val viewStatsBtn  get() = if (language == AppLanguage.VI) "Xem thống kê" else "View Stats"

    fun solvedIn(n: Int) =
        if (language == AppLanguage.VI) "Đoán đúng sau $n lượt"
        else "Solved in $n guesses"

    fun theAnswerWas(word: String) =
        if (language == AppLanguage.VI) "Đáp án là $word"
        else "The answer was $word"

    // ── HintSystem ────────────────────────────────────────────────────────────
    val hintTitle           get() = if (language == AppLanguage.VI) "Gợi ý"                         else "Hints"
    val hintRemaining       get() = if (language == AppLanguage.VI) "Còn lại"                        else "remaining"
    val vowelHintName       get() = if (language == AppLanguage.VI) "Đếm nguyên âm"                 else "Vowel Count"
    val vowelHintDesc       get() = if (language == AppLanguage.VI) "Có bao nhiêu nguyên âm trong đáp án?" else "How many vowels are in the answer?"
    val letterHintName      get() = if (language == AppLanguage.VI) "Mở chữ cái"                    else "Reveal a Letter"
    val letterHintDesc      get() = if (language == AppLanguage.VI) "Mở một chữ cái đúng vị trí"    else "Reveals one correctly-positioned letter"
    val aiHintName          get() = if (language == AppLanguage.VI) "Gợi ý AI thông minh"           else "AI Smart Hint"
    val aiHintDesc          get() = if (language == AppLanguage.VI) "AI mô tả nghĩa của từ bí mật"  else "AI describes what the word refers to"
    val hintUsed            get() = if (language == AppLanguage.VI) "✓ Đã dùng"                     else "✓ Used"
    val hintClose           get() = if (language == AppLanguage.VI) "Đóng"                          else "Close"
    val noHints             get() = if (language == AppLanguage.VI) "Hết gợi ý"                     else "No hints"
    val hint                get() = if (language == AppLanguage.VI) "Gợi ý"                         else "Hint"
    val aiThinking          get() = if (language == AppLanguage.VI) "AI đang phân tích bàn chơi…"   else "AI is analysing your board…"

    // ── SettingsScreen ────────────────────────────────────────────────────────
    val languageSetting     get() = if (language == AppLanguage.VI) "Ngôn ngữ"                      else "Language"
    val languageDesc        get() = if (language == AppLanguage.VI) "Ngôn ngữ hiển thị của ứng dụng" else "Display language for the app"
    val langEnglish         get() = "English"    // always shown in English for discoverability
    val langVietnamese      get() = "Tiếng Việt" // always shown in Vietnamese
    val animationSetting    get() = if (language == AppLanguage.VI) "Hiệu ứng động"                 else "Animations"
    val animationDesc       get() = if (language == AppLanguage.VI) "Hiệu ứng lật ô và rung hàng"   else "Keep tile feedback and row shake effects enabled."
    val hardModeSetting     get() = if (language == AppLanguage.VI) "Chế độ khó"                    else "Hard mode"
    val hardModeDesc        get() = if (language == AppLanguage.VI) "Quy tắc Wordle nghiêm ngặt hơn" else "Reserved for stricter Wordle-style rules."

    // ── Online word validation ────────────────────────────────────────────────
    val checkingWord get() = if (language == AppLanguage.VI) "Đang kiểm tra từ điển online…" else "Checking online dictionary…"
}
