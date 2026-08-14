package dev.johnoreilly.wordmaster.androidApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevToolsPanel(
    answer: String,
    onShowAnswer: () -> Unit,
    onFillAnswer: () -> Unit,
    onAutoWin: () -> Unit,
    onAutoLose: () -> Unit,
    onResetStats: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF27313B).copy(alpha = 0.92f))
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Debug only",
                color = Color(0xFFE4C85A),
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Answer: $answer",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevButton("Show", onShowAnswer, Modifier.weight(1f))
                DevButton("Fill", onFillAnswer, Modifier.weight(1f))
                DevButton("Win", onAutoWin, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevButton("Lose", onAutoLose, Modifier.weight(1f))
                DevButton("Reset Stats", onResetStats, Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF27313B)
        )
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
