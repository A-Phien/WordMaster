package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.johnoreilly.wordmaster.shared.AppLanguage
import dev.johnoreilly.wordmaster.shared.AppStrings
import dev.johnoreilly.wordmaster.shared.GameStatus
import dev.johnoreilly.wordmaster.shared.GameStats
import dev.johnoreilly.wordmaster.shared.WordMasterService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AppScreen {
    Home, Game, Stats, Settings
}

@Composable
fun WordMasterApp() {
    val context = LocalContext.current

    // ── Service & Stores ─────────────────────────────────────────────────────
    val wordMasterService = remember {
        val wordsPath = "${context.filesDir.absolutePath}/words.txt"
        WordMasterService(wordsPath, geminiApiKey = BuildConfig.GEMINI_API_KEY)
    }
    val statsStore     = remember { StatsStore(context) }
    val gameStateStore = remember { GameStateStore(context) }
    val languageStore  = remember { LanguageStore(context) }

    var currentScreen  by remember { mutableStateOf(AppScreen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope          = rememberCoroutineScope()

    // ── Persistence guards ────────────────────────────────────────────────────
    var hasLoadedStats     by remember { mutableStateOf(false) }
    var hasLoadedGameState by remember { mutableStateOf(false) }

    // ── Startup: load language → stats → game state (sequential) ─────────────
    LaunchedEffect(Unit) {
        // 0. Restore language first so strings are correct immediately
        val savedLang = languageStore.languageFlow.first()
        wordMasterService.setLanguage(savedLang)

        // 1. Restore stats
        val savedStats: GameStats = statsStore.statsFlow.first()
        if (savedStats.gamesPlayed > 0) {
            wordMasterService.restoreStats(savedStats)
        }
        hasLoadedStats = true

        // 2. Restore in-progress game (if one was saved while PLAYING)
        gameStateStore.restoreGame(wordMasterService)
        hasLoadedGameState = true
    }

    // ── Stats auto-save ───────────────────────────────────────────────────────
    val gameStats by wordMasterService.gameStats.collectAsStateWithLifecycle()
    LaunchedEffect(gameStats) {
        if (!hasLoadedStats) return@LaunchedEffect
        statsStore.saveStats(gameStats)
    }

    // ── Language — observe and persist ────────────────────────────────────────
    val appLanguage by wordMasterService.appLanguage.collectAsStateWithLifecycle()
    val strings = AppStrings(appLanguage)
    // Save whenever language changes (but not on first emission before load)
    var hasLoadedLang by remember { mutableStateOf(false) }
    LaunchedEffect(appLanguage) {
        if (!hasLoadedLang) { hasLoadedLang = true; return@LaunchedEffect }
        languageStore.saveLanguage(appLanguage)
    }

    // ── Game-state auto-save ──────────────────────────────────────────────────
    val boardGuesses   by wordMasterService.boardGuesses.collectAsStateWithLifecycle()
    val boardStatus    by wordMasterService.boardStatus.collectAsStateWithLifecycle()
    val gameStatus     by wordMasterService.gameStatus.collectAsStateWithLifecycle()
    val vowelHintUsed  by wordMasterService.vowelHintUsed.collectAsStateWithLifecycle()
    val letterHintUsed by wordMasterService.letterHintUsed.collectAsStateWithLifecycle()

    LaunchedEffect(boardGuesses, boardStatus, vowelHintUsed, letterHintUsed) {
        if (!hasLoadedGameState) return@LaunchedEffect
        if (gameStatus != GameStatus.PLAYING) return@LaunchedEffect
        val hasAnyInput = boardGuesses.any { row -> row.any { it.isNotEmpty() } }
        if (hasAnyInput) gameStateStore.saveGame(wordMasterService)
        else gameStateStore.clearGame()
    }

    LaunchedEffect(gameStatus) {
        if (!hasLoadedGameState) return@LaunchedEffect
        if (gameStatus != GameStatus.PLAYING) gameStateStore.clearGame()
    }
    // ──────────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.Home) {
                WordMasterTopAppBar(
                    title = when (currentScreen) {
                        AppScreen.Home     -> "WordMaster"
                        AppScreen.Game     -> strings.gameTitle
                        AppScreen.Stats    -> strings.statsTitle
                        AppScreen.Settings -> strings.settingsTitle
                    },
                    backLabel = strings.back,
                    onBack = { currentScreen = AppScreen.Home }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                AppScreen.Home -> HomeScreen(
                    wordMasterService = wordMasterService,
                    strings = strings,
                    onPlay = {
                        wordMasterService.resetGame()
                        currentScreen = AppScreen.Game
                    },
                    onStats    = { currentScreen = AppScreen.Stats },
                    onSettings = { currentScreen = AppScreen.Settings }
                )

                AppScreen.Game -> GameScreen(
                    wordMasterService = wordMasterService,
                    strings = strings,
                    snackbarHostState = snackbarHostState,
                    onStats = { currentScreen = AppScreen.Stats },
                    onResetStats = {
                        wordMasterService.resetStatsForDebug()
                        scope.launch { statsStore.clearStats() }
                    }
                )

                AppScreen.Stats    -> StatsScreen(wordMasterService, strings)
                AppScreen.Settings -> SettingsScreen(
                    strings = strings,
                    currentLanguage = appLanguage,
                    onLanguageChange = { lang ->
                        wordMasterService.setLanguage(lang)
                        scope.launch { languageStore.saveLanguage(lang) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordMasterTopAppBar(title: String, backLabel: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(onClick = onBack) { Text(backLabel) }
        }
    )
}
