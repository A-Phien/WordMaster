package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.johnoreilly.wordmaster.shared.AppStrings

// ─────────────────────────────────────────────────────────────────────────────
// HintButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HintButton(
    hintsAvailable: Boolean,
    gameActive: Boolean,
    strings: AppStrings,
    isAiThinking: Boolean = false,
    onClick: () -> Unit
) {
    val isEnabled = hintsAvailable && gameActive && !isAiThinking
    Box(
        Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isEnabled) Color(0xFFF0EBD8) else Color(0xFFDDD8CC))
            .border(
                width = 1.dp,
                color = if (isEnabled) Color(0xFFE4C85A).copy(alpha = 0.7f)
                        else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val emoji = if (isAiThinking) "⏳" else "💡"
            Text(text = emoji, fontSize = 26.sp)
            Text(
                text = when {
                    isAiThinking    -> "Thinking…"
                    !hintsAvailable -> strings.noHints
                    else            -> strings.hint
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color(0xFF496B5A) else Color(0xFF9DA8A2)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HintSelectionDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HintSelectionDialog(
    vowelHintUsed: Boolean,
    letterHintUsed: Boolean,
    aiHintUsed: Boolean,
    strings: AppStrings,
    onVowelHint: () -> Unit,
    onLetterHint: () -> Unit,
    onAiSmartHint: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D24))
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Header ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("💡", fontSize = 20.sp)
                    Text(
                        text = strings.hintTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    val remaining = listOf(vowelHintUsed, letterHintUsed, aiHintUsed).count { !it }
                    Text(
                        text = "$remaining ${strings.hintRemaining}",
                        fontSize = 12.sp,
                        color = Color(0xFFE4C85A),
                        fontWeight = FontWeight.Bold
                    )
                }

                // ── Hint 1: Vowel Count ──
                HintOptionRow(
                    emoji       = "🔢",
                    title       = strings.vowelHintName,
                    description = strings.vowelHintDesc,
                    penalty     = "−50 pts",
                    used        = vowelHintUsed,
                    usedLabel   = strings.hintUsed,
                    onClick     = onVowelHint
                )

                // ── Hint 2: Reveal a Letter ──
                HintOptionRow(
                    emoji       = "🔡",
                    title       = strings.letterHintName,
                    description = strings.letterHintDesc,
                    penalty     = "−100 pts",
                    used        = letterHintUsed,
                    usedLabel   = strings.hintUsed,
                    onClick     = onLetterHint
                )

                // ── Hint 3: AI Smart Hint ──
                HintOptionRow(
                    emoji       = "🤖",
                    title       = strings.aiHintName,
                    description = strings.aiHintDesc,
                    penalty     = "−150 pts",
                    used        = aiHintUsed,
                    usedLabel   = strings.hintUsed,
                    onClick     = onAiSmartHint
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(strings.hintClose, color = Color(0xFFB0BEC5))
                }
            }
        }
    }
}

@Composable
private fun HintOptionRow(
    emoji: String,
    title: String,
    description: String,
    penalty: String,
    used: Boolean,
    usedLabel: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (used) Color(0xFF263028) else Color(0xFF3A5040))
            .then(if (!used) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(emoji, fontSize = 22.sp)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = if (used) Color(0xFF6B7B70) else Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (used) Color(0xFF556B5E) else Color(0xFF9DB8A8)
                )
                if (used) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = usedLabel,
                        fontSize = 11.sp,
                        color = Color(0xFF9DB8A8),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = penalty,
                color = if (used) Color(0xFF4A5E52) else Color(0xFFE4C85A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AiThinkingCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiThinkingCard(strings: AppStrings) {
    val infinite = rememberInfiniteTransition(label = "ai_pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D24).copy(alpha = 0.92f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🤖",
                fontSize = 20.sp,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
            Text(
                text = strings.aiThinking,
                color = Color(0xFFE4C85A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HintMessagesSection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HintMessagesSection(messages: List<String>) {
    if (messages.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D24).copy(alpha = 0.92f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            messages.forEach { msg ->
                val isAiMsg = msg.startsWith("🤖")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isAiMsg) Text("💡", fontSize = 13.sp)
                    Text(
                        text = msg,
                        color = if (isAiMsg) Color(0xFF7EC8A4) else Color(0xFFE4C85A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WordCheckingCard — shown while online dictionary validation is in progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WordCheckingCard(strings: dev.johnoreilly.wordmaster.shared.AppStrings) {
    val infinite = rememberInfiniteTransition(label = "check_pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "check_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A38).copy(alpha = 0.92f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📖",
                fontSize = 20.sp,
                modifier = androidx.compose.ui.Modifier.graphicsLayer { this.alpha = alpha }
            )
            Text(
                text = strings.checkingWord,
                color = Color(0xFF82B4E8),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
