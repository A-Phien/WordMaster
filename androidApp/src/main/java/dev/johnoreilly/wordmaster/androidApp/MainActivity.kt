package dev.johnoreilly.wordmaster.androidApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.johnoreilly.wordmaster.androidApp.theme.WordMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            WordMasterTheme {
                WordMasterApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.resumeBgm()
    }

    override fun onPause() {
        super.onPause()
        SoundManager.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
