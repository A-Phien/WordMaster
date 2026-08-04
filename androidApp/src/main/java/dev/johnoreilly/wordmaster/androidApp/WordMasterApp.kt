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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.johnoreilly.wordmaster.shared.WordMasterService

private enum class AppScreen {
    Home, Game, Stats, Settings
}

@Composable
fun WordMasterApp() {
    val context = LocalContext.current
    val wordMasterService = remember {
        val wordsPath = "${context.filesDir.absolutePath}/words.txt"
        WordMasterService(wordsPath)
    }
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.Home) {
                WordMasterTopAppBar(
                    title = when (currentScreen) {
                        AppScreen.Home -> "WordMaster"
                        AppScreen.Game -> "Game"
                        AppScreen.Stats -> "Statistics"
                        AppScreen.Settings -> "Settings"
                    },
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
                    onPlay = {
                        wordMasterService.resetGame()
                        currentScreen = AppScreen.Game
                    },
                    onStats = { currentScreen = AppScreen.Stats },
                    onSettings = { currentScreen = AppScreen.Settings }
                )

                AppScreen.Game -> GameScreen(
                    wordMasterService = wordMasterService,
                    snackbarHostState = snackbarHostState,
                    onStats = { currentScreen = AppScreen.Stats }
                )

                AppScreen.Stats -> StatsScreen(wordMasterService)
                AppScreen.Settings -> SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordMasterTopAppBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    )
}
