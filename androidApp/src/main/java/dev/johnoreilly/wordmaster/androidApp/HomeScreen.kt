package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.AppStrings
import dev.johnoreilly.wordmaster.shared.WordMasterService

@Composable
fun HomeScreen(
    wordMasterService: WordMasterService,
    strings: AppStrings,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    val stats      by wordMasterService.gameStats.collectAsStateWithLifecycle()
    val wordLength by wordMasterService.wordLength.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        WordMasterBackground(scrimAlpha = 0.08f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.56f to Color.White.copy(alpha = 0.05f),
                        1f to Color.White.copy(alpha = 0.82f)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(260.dp))
            AnimatedHeroTiles()
            Spacer(Modifier.height(250.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.guessHiddenWord,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2F3A34)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassStatTile(strings.played, stats.gamesPlayed.toString(), Modifier.weight(1f))
                    GlassStatTile(strings.winRate, "${stats.winRate}%", Modifier.weight(1f))
                    GlassStatTile(strings.streak, stats.currentStreak.toString(), Modifier.weight(1f))
                }

                // ── Word length selector ──────────────────────────────────────
                WordLengthSelector(
                    label    = strings.wordLengthLabel,
                    lengths  = WordMasterService.SUPPORTED_LENGTHS,
                    selected = wordLength,
                    labelFor = { strings.letters(it) },
                    onSelect = { wordMasterService.setWordLength(it) }
                )

                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF496B5A),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(strings.playNow, fontSize = 17.sp, fontWeight = FontWeight.Black)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onStats,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(strings.stats)
                    }
                    OutlinedButton(
                        onClick = onSettings,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(strings.settings)
                    }
                }
            }
        }
    }
}

// ── Word length chip selector ─────────────────────────────────────────────────

@Composable
private fun WordLengthSelector(
    label: String,
    lengths: List<Int>,
    selected: Int,
    labelFor: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF496B5A)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            lengths.forEach { n ->
                if (n == selected) {
                    Button(
                        onClick = { onSelect(n) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF496B5A),
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(labelFor(n), fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(n) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF496B5A))
                    ) {
                        Text(labelFor(n), color = Color(0xFF496B5A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
