package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.AppStrings
import dev.johnoreilly.wordmaster.shared.GameStatus
import dev.johnoreilly.wordmaster.shared.LetterStatus
import dev.johnoreilly.wordmaster.shared.WordMasterService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GUESS_TIMER_SECONDS = 60

@Composable
fun GameScreen(
    wordMasterService: WordMasterService,
    strings: AppStrings,
    snackbarHostState: SnackbarHostState,
    onStats: () -> Unit,
    onResetStats: () -> Unit = { wordMasterService.resetStatsForDebug() }
) {
    val boardGuesses by wordMasterService.boardGuesses.collectAsStateWithLifecycle()
    val boardStatus by wordMasterService.boardStatus.collectAsStateWithLifecycle()
    val keyStatus by wordMasterService.keyStatus.collectAsStateWithLifecycle()
    val revealedAnswer by wordMasterService.revealedAnswer.collectAsStateWithLifecycle()
    val guessError by wordMasterService.guessError.collectAsStateWithLifecycle()
    val gameStatus by wordMasterService.gameStatus.collectAsStateWithLifecycle()
    val currentScore by wordMasterService.currentScore.collectAsStateWithLifecycle()
    val stats by wordMasterService.gameStats.collectAsStateWithLifecycle()

    // ── Hint state ────────────────────────────────────────────────────────────
    val vowelHintUsed by wordMasterService.vowelHintUsed.collectAsStateWithLifecycle()
    val letterHintUsed by wordMasterService.letterHintUsed.collectAsStateWithLifecycle()
    val aiHintUsed by wordMasterService.aiHintUsed.collectAsStateWithLifecycle()
    val isAiThinking by wordMasterService.isAiThinking.collectAsStateWithLifecycle()
    val isValidatingWord by wordMasterService.isValidatingWord.collectAsStateWithLifecycle()
    val hintMessages by wordMasterService.hintMessages.collectAsStateWithLifecycle()
    var showHintDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // ─────────────────────────────────────────────────────────────────────────

    // ── Flip-animation state ─────────────────────────────────────────────────
    val scoredRowsCount = boardStatus.count { row ->
        row.all { it != LetterStatus.UNGUESSED }
    }
    var animatingRowIndex by remember { mutableStateOf<Int?>(null) }
    var showResult by remember { mutableStateOf(false) }

    LaunchedEffect(scoredRowsCount) {
        if (scoredRowsCount == 0) {
            animatingRowIndex = null
            showResult = false
            return@LaunchedEffect
        }
        val rowToFlip = scoredRowsCount - 1
        animatingRowIndex = rowToFlip
        val waitMs = (wordMasterService.wordLength.value - 1) * 120L + 300L
        kotlinx.coroutines.delay(waitMs)
        animatingRowIndex = null
        if (gameStatus != GameStatus.PLAYING) {
            showResult = true
        }
    }

    // For timeout / revealAndLose: gameStatus flips to LOST without changing
    // scoredRowsCount, so the flip-animation LaunchedEffect never fires.
    // This one catches those cases and shows the ResultSheet after a short pause.
    LaunchedEffect(gameStatus) {
        if (gameStatus == GameStatus.PLAYING) {
            showResult = false   // clear on new game
            return@LaunchedEffect
        }
        delay(400L)   // brief pause so it feels intentional, not jarring
        showResult = true
    }
    // ────────────────────────────────────────────────────────────────────────

    // ── Per-guess countdown timer ─────────────────────────────────────────────
    var timeLeft by remember { mutableIntStateOf(GUESS_TIMER_SECONDS) }

    // Restart countdown whenever a guess row is completed OR game status changes
    LaunchedEffect(scoredRowsCount, gameStatus) {
        if (gameStatus != GameStatus.PLAYING) {
            timeLeft = GUESS_TIMER_SECONDS   // reset display when game ends
            return@LaunchedEffect
        }
        timeLeft = GUESS_TIMER_SECONDS
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
        // Time's up — burn this attempt
        wordMasterService.timeoutGuess()
    }
    // ─────────────────────────────────────────────────────────────────────────

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(guessError) {
        val error = guessError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        val shift = 16f
        for (step in listOf(-shift, shift, -shift, shift, -shift / 2, shift / 2, 0f)) {
            shakeOffset.animateTo(step)
        }
        wordMasterService.clearGuessError()
    }

    Box(Modifier.fillMaxSize()) {
        WordMasterBackground(scrimAlpha = 0.86f)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(strings.score, currentScore.toString(), Modifier.weight(1f))
                StatTile(strings.streak, stats.currentStreak.toString(), Modifier.weight(1f))
                HintButton(
                    hintsAvailable = !vowelHintUsed || !letterHintUsed || !aiHintUsed,
                    gameActive = gameStatus == GameStatus.PLAYING,
                    strings = strings,
                    isAiThinking = isAiThinking,
                    onClick = { showHintDialog = true }
                )
            }

            // ── Countdown timer bar ───────────────────────────────────────────
            if (gameStatus == GameStatus.PLAYING) {
                val timerColor = when {
                    timeLeft > 30 -> Color(0xFF2E7D32)   // 🟢 xanh — nhiều giờ
                    timeLeft > 10 -> Color(0xFFCC8800)   // 🟡 vàng — sắp hết
                    else          -> Color(0xFFC62828)   // 🔴 đỏ — nguy hiểm
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Progress track
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x22000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(timeLeft.toFloat() / GUESS_TIMER_SECONDS)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(timerColor)
                        )
                    }
                    // Seconds label
                    Text(
                        text = "${timeLeft}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = timerColor,
                        modifier = Modifier.width(32.dp)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // Scale tile size so all letters fit on one row regardless of word length
                // 3-4 letters: 52dp, 5 letters: 50dp, 6 letters: 46dp
                val wordLen = wordMasterService.wordLength.value
                val tileSize = when {
                    wordLen >= 6 -> 46.dp
                    wordLen == 5 -> 50.dp
                    else         -> 52.dp
                }
                Column(
                    Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (guessAttempt in 0 until WordMasterService.MAX_NUMBER_OF_GUESSES) {
                        val rowModifier = if (guessAttempt == wordMasterService.currentGuessAttempt) {
                            Modifier.offset { IntOffset(shakeOffset.value.toInt(), 0) }
                        } else {
                            Modifier
                        }
                        Row(rowModifier, horizontalArrangement = Arrangement.Center) {
                            for (character in 0 until wordMasterService.wordLength.value) {
                                val isFlippingThisRow = animatingRowIndex == guessAttempt
                                LetterTile(
                                    letter = boardGuesses[guessAttempt][character],
                                    status = boardStatus[guessAttempt][character],
                                    shouldFlip = isFlippingThisRow,
                                    flipDelayMs = if (isFlippingThisRow) character * 120 else 0,
                                    tileSize = tileSize
                                )
                            }
                        }
                    }
                }
            }

            // Hint messages (shown below the board when hints are used)
            HintMessagesSection(hintMessages)

            // AI thinking indicator
            if (isAiThinking) AiThinkingCard(strings)

            if (revealedAnswer != null) {
                Text(
                    text = strings.answerLabel + revealedAnswer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Inline validation indicator (only shown during online check)
            if (isValidatingWord) {
                WordCheckingCard(strings)
            }

            Keyboard(
                keyStatus = keyStatus,
                enabled = !isValidatingWord,
                onLetter = { wordMasterService.addLetter(it) },
                onEnter = {
                    coroutineScope.launch {
                        wordMasterService.submitGuessAsync()
                    }
                },
                onDelete = { wordMasterService.removeLetter() }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { wordMasterService.resetGame() }) {
                    Text(strings.newGame)
                }
                OutlinedButton(onClick = onStats) {
                    Text(strings.viewStats)
                }
            }

            // "Xem đáp án" — reveals answer and immediately loses the game
            if (gameStatus == GameStatus.PLAYING) {
                OutlinedButton(onClick = { wordMasterService.revealAndLose() }) {
                    Text(strings.showAnswer)
                }
            }
        }
    }

    // Show ResultSheet only after the flip animation of the final row has completed
    if (showResult) {
        ResultSheet(
            gameStatus = gameStatus,
            currentScore = currentScore,
            guessesUsed = wordMasterService.currentGuessAttempt,
            answer = revealedAnswer ?: wordMasterService.answer,
            boardStatus = boardStatus,
            wordLength = wordMasterService.wordLength.value,
            strings = strings,
            pixabayApiKey = BuildConfig.PIXABAY_API_KEY,
            onPlayAgain = { wordMasterService.resetGame() },
            onStats = onStats
        )
    }

    // Hint picker dialog
    if (showHintDialog) {
        HintSelectionDialog(
            vowelHintUsed = vowelHintUsed,
            letterHintUsed = letterHintUsed,
            aiHintUsed = aiHintUsed,
            strings = strings,
            onVowelHint = {
                wordMasterService.useVowelHint()
                showHintDialog = false
            },
            onLetterHint = {
                wordMasterService.useRevealLetterHint()
                showHintDialog = false
            },
            onAiSmartHint = {
                showHintDialog = false
                coroutineScope.launch {
                    wordMasterService.requestAiSmartHint()
                }
            },
            onDismiss = { showHintDialog = false }
        )
    }
}
