package dev.johnoreilly.wordmaster.androidApp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.johnoreilly.wordmaster.shared.GameStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── DataStore singleton (one per app process via Kotlin property delegate) ──
private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "word_master_stats")

/**
 * Preferences DataStore wrapper for persisting [GameStats].
 *
 * All keys are prefixed with "stats_" to avoid future key collisions.
 */
class StatsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    // ── Preference keys ──────────────────────────────────────────────────────
    private object Keys {
        val GAMES_PLAYED   = intPreferencesKey("stats_games_played")
        val WINS           = intPreferencesKey("stats_wins")
        val LOSSES         = intPreferencesKey("stats_losses")
        val CURRENT_STREAK = intPreferencesKey("stats_current_streak")
        val MAX_STREAK     = intPreferencesKey("stats_max_streak")
        val TOTAL_SCORE    = intPreferencesKey("stats_total_score")
        // guessDistribution keys: dist_1 … dist_6
        fun dist(n: Int) = intPreferencesKey("stats_dist_$n")
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * A cold [Flow] that emits the current persisted [GameStats] and every
     * subsequent update. Reading is non-blocking; the first value is emitted
     * as soon as DataStore finishes its initial disk read.
     */
    val statsFlow: Flow<GameStats> = store.data.map { prefs ->
        val distribution = (1..6).mapNotNull { n ->
            val v = prefs[Keys.dist(n)] ?: 0
            if (v > 0) n to v else null
        }.toMap()

        GameStats(
            gamesPlayed      = prefs[Keys.GAMES_PLAYED]   ?: 0,
            wins             = prefs[Keys.WINS]           ?: 0,
            losses           = prefs[Keys.LOSSES]         ?: 0,
            currentStreak    = prefs[Keys.CURRENT_STREAK] ?: 0,
            maxStreak        = prefs[Keys.MAX_STREAK]     ?: 0,
            totalScore       = prefs[Keys.TOTAL_SCORE]    ?: 0,
            guessDistribution = distribution
        )
    }

    /** Atomically write all fields of [stats] to DataStore. */
    suspend fun saveStats(stats: GameStats) {
        store.edit { prefs ->
            prefs[Keys.GAMES_PLAYED]   = stats.gamesPlayed
            prefs[Keys.WINS]           = stats.wins
            prefs[Keys.LOSSES]         = stats.losses
            prefs[Keys.CURRENT_STREAK] = stats.currentStreak
            prefs[Keys.MAX_STREAK]     = stats.maxStreak
            prefs[Keys.TOTAL_SCORE]    = stats.totalScore
            // Write all six distribution slots (0 if not present)
            for (n in 1..6) {
                prefs[Keys.dist(n)] = stats.guessDistribution[n] ?: 0
            }
        }
    }

    /** Reset all persisted stats to zero (used by DevTools "Reset Stats"). */
    suspend fun clearStats() {
        store.edit { prefs ->
            prefs[Keys.GAMES_PLAYED]   = 0
            prefs[Keys.WINS]           = 0
            prefs[Keys.LOSSES]         = 0
            prefs[Keys.CURRENT_STREAK] = 0
            prefs[Keys.MAX_STREAK]     = 0
            prefs[Keys.TOTAL_SCORE]    = 0
            for (n in 1..6) { prefs[Keys.dist(n)] = 0 }
        }
    }
}
