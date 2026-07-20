import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import dev.johnoreilly.wordmaster.shared.LetterStatus
import dev.johnoreilly.wordmaster.shared.WordMasterService

fun main() = singleWindowApplication(
    title = "WordMaster KMP",
    state = WindowState(size = DpSize(560.dp, 760.dp))
) {
    WordMasterView()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WordMasterView() {
    val wordMasterService = remember { WordMasterService("words.txt") }

    val boardGuesses by wordMasterService.boardGuesses.collectAsState()
    val boardStatus by wordMasterService.boardStatus.collectAsState()
    val keyStatus by wordMasterService.keyStatus.collectAsState()
    val revealedAnswer by wordMasterService.revealedAnswer.collectAsState()
    val lastGuessCorrect by wordMasterService.lastGuessCorrect.collectAsState()
    val guessError by wordMasterService.guessError.collectAsState()

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // Horizontal shake offset applied to the active row when a guess is rejected.
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(guessError) {
        if (guessError == null) return@LaunchedEffect
        val shift = 16f
        for (step in listOf(-shift, shift, -shift, shift, -shift / 2, shift / 2, 0f)) {
            shakeOffset.animateTo(step)
        }
        wordMasterService.clearGuessError()
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        wordMasterService.submitGuess(); true
                    }
                    Key.Backspace, Key.Delete -> {
                        wordMasterService.removeLetter(); true
                    }
                    else -> {
                        val char = event.utf16CodePoint.toChar()
                        if (char.isLetter()) {
                            wordMasterService.addLetter(char.toString()); true
                        } else false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (guessAttempt in 0 until WordMasterService.MAX_NUMBER_OF_GUESSES) {
                val rowModifier = if (guessAttempt == wordMasterService.currentGuessAttempt) {
                    Modifier.offset { IntOffset(shakeOffset.value.toInt(), 0) }
                } else {
                    Modifier
                }
                Row(rowModifier, horizontalArrangement = Arrangement.Center) {
                    for (character in 0 until WordMasterService.NUMBER_LETTERS) {
                        LetterTile(
                            letter = boardGuesses[guessAttempt][character],
                            status = boardStatus[guessAttempt][character]
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            guessError?.let {
                Text(it, fontSize = 16.sp, color = Color(0xFFB00020), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }

            if (revealedAnswer != null) {
                Text("Answer: $revealedAnswer", fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
            }

            Keyboard(
                keyStatus = keyStatus,
                onLetter = { wordMasterService.addLetter(it) },
                onEnter = { wordMasterService.submitGuess() },
                onDelete = { wordMasterService.removeLetter() }
            )

            Spacer(Modifier.height(12.dp))

            Button(onClick = {
                wordMasterService.resetGame()
                rootFocus.requestFocus()
            }) {
                Text("New Game")
            }

            if (lastGuessCorrect) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("You win!") },
                    text = { Text("Great job guessing the word.") },
                    confirmButton = {
                        Button(onClick = {
                            wordMasterService.resetGame()
                            rootFocus.requestFocus()
                        }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LetterTile(letter: String, status: LetterStatus) {
    Box(
        Modifier
            .padding(3.dp)
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(mapLetterStatusToBackgroundColor(status))
            .border(1.5.dp, Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = mapLetterStatusToTextColor(status),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val KEYBOARD_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
private fun Keyboard(
    keyStatus: Map<String, LetterStatus>,
    onLetter: (String) -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KEYBOARD_ROWS.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.Center) {
                if (index == KEYBOARD_ROWS.size - 1) {
                    KeyButton("ENTER", onClick = onEnter, flexWidth = true)
                }
                row.forEach { char ->
                    val letter = char.toString()
                    KeyButton(
                        label = letter,
                        onClick = { onLetter(letter) },
                        status = keyStatus[letter] ?: LetterStatus.UNGUESSED
                    )
                }
                if (index == KEYBOARD_ROWS.size - 1) {
                    KeyButton("DEL", onClick = onDelete, flexWidth = true)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    status: LetterStatus = LetterStatus.UNGUESSED,
    flexWidth: Boolean = false
) {
    val background = if (status == LetterStatus.UNGUESSED) Color(0xFFD3D6DA) else mapLetterStatusToBackgroundColor(status)
    val textColor = if (status == LetterStatus.UNGUESSED) Black else mapLetterStatusToTextColor(status)

    Box(
        Modifier
            .padding(2.dp)
            .height(52.dp)
            .then(if (flexWidth) Modifier.width(56.dp) else Modifier.width(38.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (flexWidth) 12.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun mapLetterStatusToBackgroundColor(letterStatus: LetterStatus): Color {
    return when (letterStatus) {
        LetterStatus.UNGUESSED -> White
        LetterStatus.CORRECT_POSITION -> Color(0xFF2E7D32)
        LetterStatus.INCORRECT_POSITION -> Color(0xFF9B870C)
        LetterStatus.NOT_IN_WORD -> Color(0xFF787C7E)
    }
}

fun mapLetterStatusToTextColor(letterStatus: LetterStatus): Color {
    return when (letterStatus) {
        LetterStatus.UNGUESSED -> Black
        LetterStatus.CORRECT_POSITION -> White
        LetterStatus.INCORRECT_POSITION -> White
        LetterStatus.NOT_IN_WORD -> White
    }
}
