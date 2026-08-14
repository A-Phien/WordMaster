package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.AppStrings
import dev.johnoreilly.wordmaster.shared.GameStats
import dev.johnoreilly.wordmaster.shared.WordMasterService

@Composable
fun StatsScreen(wordMasterService: WordMasterService, strings: AppStrings) {
    val stats by wordMasterService.gameStats.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(strings.played,  stats.gamesPlayed.toString(), Modifier.weight(1f))
            StatTile(strings.wins,    stats.wins.toString(),        Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(strings.winRate, "${stats.winRate}%",          Modifier.weight(1f))
            StatTile(strings.losses,  stats.losses.toString(),      Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(strings.currentStreak, stats.currentStreak.toString(), Modifier.weight(1f))
            StatTile(strings.maxStreak,     stats.maxStreak.toString(),     Modifier.weight(1f))
        }
        StatTile(strings.totalScore, stats.totalScore.toString(), Modifier.fillMaxWidth())

        Text(
            text = strings.guessDist,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        GuessDistribution(stats)
    }
}

@Composable
private fun GuessDistribution(stats: GameStats) {
    val maxCount = maxOf(1, stats.guessDistribution.values.maxOrNull() ?: 0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (guess in 1..WordMasterService.MAX_NUMBER_OF_GUESSES) {
            val count = stats.guessDistribution[guess] ?: 0
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(guess.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / maxCount)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = count.toString(),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp),
                        color = if (count > 0) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
