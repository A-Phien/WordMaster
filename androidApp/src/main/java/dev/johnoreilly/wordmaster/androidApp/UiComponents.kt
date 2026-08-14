package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.johnoreilly.wordmaster.shared.LetterStatus
import kotlinx.coroutines.delay

@Composable
fun WordMasterBackground(scrimAlpha: Float) {
    Image(
        painter = painterResource(id = R.drawable.wordmaster_bg),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = scrimAlpha))
    )
}

@Composable
fun AnimatedHeroTiles() {
    val infiniteTransition = rememberInfiniteTransition()
    val lift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700),
            repeatMode = RepeatMode.Reverse
        )
    )
    val tilt by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("P", "L", "A", "Y").forEachIndexed { index, letter ->
            val isEven = index % 2 == 0
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        translationY = if (isEven) lift else -lift * 0.55f
                        rotationZ = if (isEven) tilt else -tilt
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (index) {
                            0 -> Color(0xFF496B5A)
                            1 -> Color(0xFFE4C85A)
                            2 -> Color(0xFF27313B)
                            else -> Color.White
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = if (index == 3) Color(0xFF27313B) else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun GlassStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                color = Color(0xFF27313B)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF68736C)
            )
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A single letter tile on the game board.
 *
 * When [shouldFlip] is true the tile plays a 3-D card-flip animation:
 *  • First half  (rotationX 0 → 90°): tile face turns away – shows UNGUESSED appearance.
 *  • At the midpoint the revealed [status] colour is latched in.
 *  • Second half (rotationX 90 → 0°): tile face returns – shows the result colour.
 *
 * [flipDelayMs] staggers tiles within a row (e.g. column * 120 ms).
 */
@Composable
fun LetterTile(
    letter: String,
    status: LetterStatus,
    shouldFlip: Boolean = false,
    flipDelayMs: Int = 0
) {
    // Rotation state for the flip effect
    val rotationX = remember { Animatable(0f) }
    // Whether the "revealed" (scored) face is currently showing
    var showRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(shouldFlip, status) {
        when {
            // Game reset or plain empty tile — snap back to default
            status == LetterStatus.UNGUESSED && !shouldFlip -> {
                showRevealed = false
                rotationX.snapTo(0f)
            }
            // Start the flip animation
            shouldFlip -> {
                showRevealed = false
                rotationX.snapTo(0f)
                // Per-tile stagger
                delay(flipDelayMs.toLong())
                // First half: rotate away (front disappears)
                rotationX.animateTo(
                    targetValue = 90f,
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing)
                )
                // Latch in the result colour at the midpoint (tile is edge-on = invisible)
                showRevealed = true
                // Second half: rotate back (back face reveals result colour)
                rotationX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing)
                )
            }
            // shouldFlip just became false while status is already scored:
            // animation was interrupted or completed – always show result.
            else -> {
                showRevealed = true
                rotationX.snapTo(0f)
            }
        }
    }

    // Decide which face to render
    val displayStatus = when {
        shouldFlip && !showRevealed -> LetterStatus.UNGUESSED   // front face during first half
        showRevealed                -> status                    // back face (result)
        else                        -> status                    // normal (not animating)
    }
    val isEmptyDisplay = displayStatus == LetterStatus.UNGUESSED

    Box(
        Modifier
            .padding(3.dp)
            .size(52.dp)
            .graphicsLayer {
                this.rotationX = rotationX.value
                // Increase camera distance to soften the perspective distortion
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isEmptyDisplay) Color(0xFFF8F4EA) else mapLetterStatusToBackgroundColor(displayStatus))
            .border(1.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isEmptyDisplay) {
            Image(
                painter = painterResource(id = R.drawable.tile_texture),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = letter,
            color = mapLetterStatusToTextColor(displayStatus),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private val KEYBOARD_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
fun Keyboard(
    keyStatus: Map<String, LetterStatus>,
    enabled: Boolean = true,
    onLetter: (String) -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f }
    ) {
        KEYBOARD_ROWS.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.Center) {
                if (index == KEYBOARD_ROWS.size - 1) {
                    KeyButton("ENTER", onClick = onEnter, flexWidth = true, enabled = enabled)
                }
                row.forEach { char ->
                    val letter = char.toString()
                    KeyButton(
                        label = letter,
                        onClick = { onLetter(letter) },
                        status = keyStatus[letter] ?: LetterStatus.UNGUESSED,
                        enabled = enabled
                    )
                }
                if (index == KEYBOARD_ROWS.size - 1) {
                    KeyButton("DEL", onClick = onDelete, flexWidth = true, enabled = enabled)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    status: LetterStatus = LetterStatus.UNGUESSED,
    flexWidth: Boolean = false,
    enabled: Boolean = true
) {
    val background = if (status == LetterStatus.UNGUESSED) {
        Color(0xFFD3D6DA)
    } else {
        mapLetterStatusToBackgroundColor(status)
    }
    val textColor = if (status == LetterStatus.UNGUESSED) Color.Black else mapLetterStatusToTextColor(status)

    Box(
        Modifier
            .padding(2.dp)
            .height(48.dp)
            .then(if (flexWidth) Modifier.width(58.dp) else Modifier.width(31.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (flexWidth) 11.sp else 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun mapLetterStatusToBackgroundColor(letterStatus: LetterStatus): Color {
    return when (letterStatus) {
        LetterStatus.UNGUESSED -> Color.White
        LetterStatus.CORRECT_POSITION -> Color(0xFF2E7D32)
        LetterStatus.INCORRECT_POSITION -> Color(0xFF9B870C)
        LetterStatus.NOT_IN_WORD -> Color(0xFF787C7E)
    }
}

fun mapLetterStatusToTextColor(letterStatus: LetterStatus): Color {
    return when (letterStatus) {
        LetterStatus.UNGUESSED -> Color.Black
        LetterStatus.CORRECT_POSITION -> Color.White
        LetterStatus.INCORRECT_POSITION -> Color.White
        LetterStatus.NOT_IN_WORD -> Color.White
    }
}
