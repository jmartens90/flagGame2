package com.flaggame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flaggame.data.GameData
import com.flaggame.data.Place
import kotlinx.coroutines.delay

@Composable
fun MultipleChoiceQuestion(data: GameData, place: Place, quiz: Quiz, pool: List<Place>, onNext: (Boolean) -> Unit) {
    val options = remember(place) {
        // Prefer look-alike distractors from the same group, then fill from anywhere.
        val usable = data.places(place.set).filter { it != place && (quiz == Quiz.FLAGS || it.capital != null) }
        val sameGroup = usable.filter { it.group == place.group }.shuffled()
        val others = (pool + usable).distinct().filter { it != place && it in usable && it !in sameGroup }.shuffled()
        (listOf(place) + (sameGroup.take(2) + others + sameGroup.drop(2)).distinct().take(3)).shuffled()
    }
    var picked by remember(place) { mutableStateOf<Place?>(null) }

    LaunchedEffect(picked) {
        if (picked == place) { delay(900); onNext(true) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlagImage(data, place, Modifier.fillMaxWidth())
        Text(questionText(place, quiz), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        options.forEach { option ->
            val color = when {
                picked == null -> MaterialTheme.colorScheme.primary
                option == place -> Correct
                option == picked -> Wrong
                else -> Color.Gray
            }
            Button(
                onClick = { if (picked == null) picked = option },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color),
            ) {
                Text(answerOf(option, quiz), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        val p = picked
        if (p != null && p != place) {
            Feedback(false, answerOf(place, quiz)) { onNext(false) }
        }
    }
}
