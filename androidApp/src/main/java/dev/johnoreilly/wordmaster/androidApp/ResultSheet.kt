package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.johnoreilly.wordmaster.shared.GameStatus
import dev.johnoreilly.wordmaster.shared.LetterStatus
import dev.johnoreilly.wordmaster.shared.WordMasterService

@Composable
fun ResultSheet(
    gameStatus: GameStatus,
    currentScore: Int,
    guessesUsed: Int,
    answer: String,
    boardStatus: ArrayList<ArrayList<LetterStatus>>,
    onPlayAgain: () -> Unit,
    onStats: () -> Unit
) {
    val won = gameStatus == GameStatus.WON
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF4))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (won) {
                    Image(
                        painter = painterResource(id = R.drawable.victory_badge),
                        contentDescription = null,
                        modifier = Modifier.size(108.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier
                            .size(86.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF27313B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    }
                }

                Text(
                    text = if (won) "Victory" else "Word Revealed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF27313B)
                )
                Text(
                    text = if (won) "Solved in $guessesUsed guesses" else "The answer was $answer",
                    color = Color(0xFF68736C),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassStatTile("Score", currentScore.toString(), Modifier.weight(1f))
                    GlassStatTile("Guesses", guessesUsed.toString(), Modifier.weight(1f))
                }

                ResultGrid(boardStatus = boardStatus, rows = guessesUsed)

                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF496B5A),
                        contentColor = Color.White
                    )
                ) {
                    Text("Play Again", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onStats) {
                    Text("View Stats")
                }
            }
        }
    }
}

@Composable
private fun ResultGrid(boardStatus: ArrayList<ArrayList<LetterStatus>>, rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows.coerceIn(1, WordMasterService.MAX_NUMBER_OF_GUESSES)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until WordMasterService.NUMBER_LETTERS) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(mapLetterStatusToBackgroundColor(boardStatus[row][column]))
                    )
                }
            }
        }
    }
}
