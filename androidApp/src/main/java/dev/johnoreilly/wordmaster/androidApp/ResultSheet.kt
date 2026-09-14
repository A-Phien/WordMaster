package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.johnoreilly.wordmaster.shared.AppStrings
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
    wordLength: Int,
    strings: AppStrings,
    pixabayApiKey: String,
    onPlayAgain: () -> Unit,
    onStats: () -> Unit
) {
    val won = gameStatus == GameStatus.WON

    // ── Fetch word illustration as Bitmap (OkHttp + BitmapFactory) ──
    var imageBitmap by remember(answer) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    var imageLoading by remember(answer) { mutableStateOf(true) }

    LaunchedEffect(answer) {
        imageLoading = true
        imageBitmap = PixabayImageLoader.fetchImageBitmap(answer, pixabayApiKey)
        imageLoading = false
    }

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

                // ── Word illustration image ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFEDF5F0)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageLoading -> {
                            CircularProgressIndicator(
                                color = Color(0xFF496B5A),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        imageBitmap != null -> {
                            // Display bitmap decoded by OkHttp+BitmapFactory (bypasses Coil)
                            Image(
                                bitmap = imageBitmap!!.asImageBitmap(),
                                contentDescription = answer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        else -> {
                            // Offline / not found fallback
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = answer,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF496B5A),
                                    letterSpacing = 4.sp
                                )
                                Text(text = "📷", fontSize = 24.sp)
                            }
                        }
                    }
                }

                // ── DEBUG: remove after confirmed ──────────────────────────────
                Text(
                    text = when {
                        imageLoading     -> "⏳ Đang tải..."
                        imageBitmap != null -> "✅ Bitmap OK"
                        else             -> "❌ Không có ảnh"
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )

                // ── Badge: victory or defeat ───────────────────────────────────
                if (won) {
                    Image(
                        painter = painterResource(id = R.drawable.victory_badge),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF27313B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                }

                // ── Title + subtitle ───────────────────────────────────────────
                Text(
                    text = if (won) strings.victory else strings.wordRevealed,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF27313B)
                )
                Text(
                    text = if (won) strings.solvedIn(guessesUsed) else strings.theAnswerWas(answer),
                    color = Color(0xFF68736C),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // ── Score + Guesses tiles ──────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassStatTile(strings.score,   currentScore.toString(),  Modifier.weight(1f))
                    GlassStatTile(strings.guesses, guessesUsed.toString(),   Modifier.weight(1f))
                }

                // ── Mini guess-result grid ────────────────────────────────────
                ResultGrid(boardStatus = boardStatus, rows = guessesUsed, cols = wordLength)

                // ── Action buttons ────────────────────────────────────────────
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF496B5A),
                        contentColor = Color.White
                    )
                ) {
                    Text(strings.playAgain, fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onStats) {
                    Text(strings.viewStatsBtn)
                }
            }
        }
    }
}

@Composable
private fun ResultGrid(boardStatus: ArrayList<ArrayList<LetterStatus>>, rows: Int, cols: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows.coerceIn(1, WordMasterService.MAX_NUMBER_OF_GUESSES)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until cols) {
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
