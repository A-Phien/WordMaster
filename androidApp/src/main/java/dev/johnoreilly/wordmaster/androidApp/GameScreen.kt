package dev.johnoreilly.wordmaster.androidApp

import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.GameStatus
import dev.johnoreilly.wordmaster.shared.LetterStatus
import dev.johnoreilly.wordmaster.shared.WordMasterService
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    wordMasterService: WordMasterService,
    snackbarHostState: SnackbarHostState,
    onStats: () -> Unit
) {
    val boardGuesses by wordMasterService.boardGuesses.collectAsStateWithLifecycle()
    val boardStatus by wordMasterService.boardStatus.collectAsStateWithLifecycle()
    val keyStatus by wordMasterService.keyStatus.collectAsStateWithLifecycle()
    val revealedAnswer by wordMasterService.revealedAnswer.collectAsStateWithLifecycle()
    val guessError by wordMasterService.guessError.collectAsStateWithLifecycle()
    val gameStatus by wordMasterService.gameStatus.collectAsStateWithLifecycle()
    val currentScore by wordMasterService.currentScore.collectAsStateWithLifecycle()
    val stats by wordMasterService.gameStats.collectAsStateWithLifecycle()
    val isDebugBuild = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var showDevTools by remember { mutableStateOf(false) }

    // ── Flip-animation state ─────────────────────────────────────────────────
    // Counts how many rows have been fully scored (all tiles != UNGUESSED).
    // Wrapped in derivedStateOf so it recomputes reactively whenever boardStatus changes.
    val scoredRowsCount by remember {
        derivedStateOf {
            boardStatus.count { row -> row.all { it != LetterStatus.UNGUESSED } }
        }
    }
    // Index of the row whose tiles are currently flipping (-1 = none).
    var animatingRowIndex by remember { mutableStateOf<Int?>(null) }
    // Gate for ResultSheet: true only after the last row's animation finishes.
    var showResult by remember { mutableStateOf(false) }

    // When scoredRowsCount changes (new guess submitted), kick off the flip
    // animation for that row, then show the ResultSheet if the game ended.
    LaunchedEffect(scoredRowsCount) {
        if (scoredRowsCount == 0) {
            // Game was reset — clear everything
            animatingRowIndex = null
            showResult = false
            return@LaunchedEffect
        }
        val rowToFlip = scoredRowsCount - 1   // 0-indexed row that was just scored
        animatingRowIndex = rowToFlip

        // Total wait = delay of last tile + one full flip duration
        // last tile delay = (N-1) * 120 ms, flip = 2 * 150 ms = 300 ms
        val waitMs = (WordMasterService.NUMBER_LETTERS - 1) * 120L + 300L
        kotlinx.coroutines.delay(waitMs)

        animatingRowIndex = null
        if (gameStatus != GameStatus.PLAYING) {
            showResult = true
        }
    }
    // ────────────────────────────────────────────────────────────────────────

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
                StatTile("Score", currentScore.toString(), Modifier.weight(1f))
                StatTile("Streak", stats.currentStreak.toString(), Modifier.weight(1f))
                AiHintButton(
                    enabled = gameStatus == GameStatus.PLAYING,
                    onClick = { snackbarHostState.showSnackbar("AI hints are coming next.") }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
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
                            for (character in 0 until WordMasterService.NUMBER_LETTERS) {
                                val isFlippingThisRow = animatingRowIndex == guessAttempt
                                LetterTile(
                                    letter = boardGuesses[guessAttempt][character],
                                    status = boardStatus[guessAttempt][character],
                                    shouldFlip = isFlippingThisRow,
                                    flipDelayMs = if (isFlippingThisRow) character * 120 else 0
                                )
                            }
                        }
                    }
                }
            }

            if (revealedAnswer != null) {
                Text(
                    text = "Answer: $revealedAnswer",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Keyboard(
                keyStatus = keyStatus,
                onLetter = { wordMasterService.addLetter(it) },
                onEnter = { wordMasterService.submitGuess() },
                onDelete = { wordMasterService.removeLetter() }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { wordMasterService.resetGame() }) {
                    Text("New Game")
                }
                OutlinedButton(onClick = onStats) {
                    Text("Stats")
                }
            }

            if (isDebugBuild) {
                OutlinedButton(onClick = { showDevTools = !showDevTools }) {
                    Text(if (showDevTools) "Hide Dev Tools" else "Dev Tools")
                }
                if (showDevTools) {
                    DevToolsPanel(
                        answer = wordMasterService.answer,
                        onShowAnswer = { wordMasterService.revealAnswerForDebug() },
                        onFillAnswer = { wordMasterService.fillAnswerForDebug() },
                        onAutoWin = { wordMasterService.winNowForDebug() },
                        onAutoLose = { wordMasterService.loseNowForDebug() },
                        onResetStats = { wordMasterService.resetStatsForDebug() }
                    )
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
            onPlayAgain = { wordMasterService.resetGame() },
            onStats = onStats
        )
    }
}

@Composable
private fun AiHintButton(enabled: Boolean, onClick: suspend () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Color(0xFFF8F4E8) else Color(0xFFE5E1D8))
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { coroutineScope.launch { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ai_hint_icon),
            contentDescription = "AI hint",
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Fit
        )
    }
}
