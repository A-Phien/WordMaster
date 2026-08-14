package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.johnoreilly.wordmaster.shared.AppLanguage
import dev.johnoreilly.wordmaster.shared.AppStrings

@Composable
fun SettingsScreen(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    var animationsEnabled by remember { mutableStateOf(true) }
    var hardModeEnabled   by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Language picker ───────────────────────────────────────────────────
        LanguageRow(
            title       = strings.languageSetting,
            description = strings.languageDesc,
            current     = currentLanguage,
            strings     = strings,
            onChange    = onLanguageChange
        )

        // ── Animations toggle ─────────────────────────────────────────────────
        SettingsRow(
            title         = strings.animationSetting,
            description   = strings.animationDesc,
            checked       = animationsEnabled,
            onCheckedChange = { animationsEnabled = it }
        )

        // ── Hard mode toggle ──────────────────────────────────────────────────
        SettingsRow(
            title         = strings.hardModeSetting,
            description   = strings.hardModeDesc,
            checked       = hardModeEnabled,
            onCheckedChange = { hardModeEnabled = it }
        )
    }
}

// ── Language row: two-button toggle (EN / VI) ─────────────────────────────────

@Composable
private fun LanguageRow(
    title: String,
    description: String,
    current: AppLanguage,
    strings: AppStrings,
    onChange: (AppLanguage) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguageChip(
                    label    = "🇬🇧  ${strings.langEnglish}",
                    selected = current == AppLanguage.EN,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChange(AppLanguage.EN) }
                )
                LanguageChip(
                    label    = "🇻🇳  ${strings.langVietnamese}",
                    selected = current == AppLanguage.VI,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChange(AppLanguage.VI) }
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF496B5A),
                contentColor   = Color.White
            )
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF496B5A))
        ) {
            Text(label, color = Color(0xFF496B5A), fontWeight = FontWeight.Bold)
        }
    }
}

// ── Generic toggle row ────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
