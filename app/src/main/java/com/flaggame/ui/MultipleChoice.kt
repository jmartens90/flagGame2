package com.flaggame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    val options = remember(place) { pickOptions(data, place, quiz, pool) }
    var picked by remember(place) { mutableStateOf<Place?>(null) }

    LaunchedEffect(picked) {
        if (picked == place) { delay(900); onNext(true) }
    }

    // Everything fits on one screen: the flag takes whatever room the question and answers leave.
    // On wide screens (tablets, landscape) the flag sits beside the answers.
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
        val wide = maxWidth > maxHeight
        val answers: @Composable () -> Unit = {
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
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
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagImage(data, place, Modifier.weight(1f).fillMaxHeight(), fitSpace = true)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) { answers() }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlagImage(data, place, Modifier.weight(1f).fillMaxWidth(), fitSpace = true)
                answers()
            }
        }
    }
}

/**
 * The right answer plus three wrong ones, all from the chosen region or group (Europe only gives
 * European options). For flags, two or three of the wrong ones are look-alikes (Ireland and
 * Ivory Coast, Senegal and Mali) to make it tricky.
 */
private fun pickOptions(data: GameData, place: Place, quiz: Quiz, pool: List<Place>): List<Place> {
    val candidates = pool.filter { it != place }
    val wrong = mutableListOf<Place>()
    if (quiz == Quiz.FLAGS) {
        val byCode = candidates.associateBy { it.code }
        val alike = place.lookalikes.mapNotNull { byCode[it] }
        wrong += alike.take(5).shuffled().take(2)
        wrong += (alike - wrong.toSet()).take(4).shuffled().take(1)
    }
    wrong += (candidates - wrong.toSet()).shuffled().take(3 - wrong.size)
    if (wrong.size < 3) {
        // Tiny region (fewer than four places): borrow from the rest of the set.
        val rest = data.places(place.set).filter {
            it != place && it !in wrong && (quiz == Quiz.FLAGS || it.capital != null)
        }
        wrong += rest.shuffled().take(3 - wrong.size)
    }
    return (listOf(place) + wrong).shuffled()
}
