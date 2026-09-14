package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.AppStrings
import dev.johnoreilly.wordmaster.shared.WordMasterService

// ── Colour palette ─────────────────────────────────────────────────────────────
private val Cream       = Color(0xFFFAF8F4)
private val CardWhite   = Color(0xFFFFFFFF)
private val BorderMint  = Color(0xFFD4EBE0)
private val RingGreen   = Color(0xFF496B5A)
private val RingGold    = Color(0xFFE4C85A)
private val TextDark    = Color(0xFF1C1C1E)
private val TextGreen   = Color(0xFF496B5A)
private val TextMuted   = Color(0xFF7A9487)
private val BarFill     = Color(0xFF496B5A)
private val BarFillBest = Color(0xFF2E6844)
private val BarTrack    = Color(0xFFD4EBE0)

private val CardShape   = RoundedCornerShape(20.dp)

// ── Main screen ────────────────────────────────────────────────────────────────
@Composable
fun StatsScreen(wordMasterService: WordMasterService, strings: AppStrings) {
    val stats by wordMasterService.gameStats.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {

        // Background — pure texture/colour, no card outlines needed in image
        Image(
            painter = painterResource(R.drawable.stats_distribution_card),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Semi-transparent cream scrim so image blends softly
        Box(Modifier.fillMaxSize().background(Cream.copy(alpha = 0.55f)))

        // ── Content ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // 1. Header card
            StatsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Decorative circle ring (Canvas-drawn, always perfect)
                    CircleRingAccent()

                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = strings.statsTitle.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = TextDark,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${stats.totalScore}  ${strings.totalScore.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextGreen,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // 2. 2×2 stat grid — each cell is its own self-contained card
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = stats.gamesPlayed.toString(),
                        label = strings.played,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${stats.winRate}%",
                        label = strings.winRate,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value = stats.currentStreak.toString(),
                        label = strings.currentStreak,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = stats.maxStreak.toString(),
                        label = strings.maxStreak,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Guess distribution card
            val maxCount  = maxOf(1, stats.guessDistribution.values.maxOrNull() ?: 0)
            val bestGuess = stats.guessDistribution.entries.maxByOrNull { it.value }?.key

            StatsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section title
                    Text(
                        text = strings.guessDist.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(2.dp))

                    // 6 animated bar rows
                    for (guess in 1..WordMasterService.MAX_NUMBER_OF_GUESSES) {
                        val count    = stats.guessDistribution[guess] ?: 0
                        val fraction = count.toFloat() / maxCount
                        val isBest   = guess == bestGuess && count > 0
                        DistributionRow(guess, count, fraction, isBest)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Helper composables ─────────────────────────────────────────────────────────

/** White card with mint border and soft shadow — all content centred inside */
@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = CardShape, ambientColor = RingGreen.copy(alpha = 0.08f))
            .clip(CardShape)
            .background(CardWhite)
            .border(1.5.dp, BorderMint, CardShape)
    ) {
        content()
    }
}

/** Single stat card: big number on top, label below — always centred in its card */
@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = CardShape, ambientColor = RingGreen.copy(alpha = 0.08f))
            .clip(CardShape)
            .background(CardWhite)
            .border(1.5.dp, BorderMint, CardShape)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = TextDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 0.6.sp,
                lineHeight = 12.sp
            )
        }
    }
}

/** Canvas-drawn open circle ring — green stroke + thin gold outer ring */
@Composable
private fun CircleRingAccent() {
    Canvas(modifier = Modifier.size(56.dp)) {
        val centerX  = size.width  / 2f
        val centerY  = size.height / 2f
        val outerR   = size.minDimension / 2f - 2.dp.toPx()
        val innerR   = outerR - 6.dp.toPx()

        // Gold outer ring
        drawCircle(
            color  = RingGold,
            radius = outerR,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style  = Stroke(width = 2.dp.toPx())
        )
        // Green inner ring
        drawCircle(
            color  = RingGreen,
            radius = innerR,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style  = Stroke(width = 5.dp.toPx())
        )
    }
}

/** One guess-distribution row: number circle + animated bar + count */
@Composable
private fun DistributionRow(guess: Int, count: Int, fraction: Float, isBest: Boolean) {
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }
    val animFraction by animateFloatAsState(
        targetValue = if (triggered) fraction.coerceAtLeast(if (count > 0) 0.05f else 0f) else 0f,
        animationSpec = tween(700, delayMillis = guess * 80),
        label = "bar_$guess"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Guess number
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isBest) BarFill.copy(alpha = 0.12f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = guess.toString(),
                fontSize = 12.sp,
                fontWeight = if (isBest) FontWeight.Black else FontWeight.Bold,
                color = if (isBest) BarFill else TextDark
            )
        }

        // Animated bar track + fill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(BarTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (isBest) BarFillBest else BarFill)
            )
        }

        // Count
        Text(
            text = count.toString(),
            modifier = Modifier.width(30.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isBest) BarFill else TextDark,
            textAlign = TextAlign.End
        )
    }
}