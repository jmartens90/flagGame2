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
import com.flaggame.data.Country
import com.flaggame.data.GameData
import kotlinx.coroutines.delay

@Composable
fun MultipleChoiceQuestion(data: GameData, country: Country, pool: List<Country>, onNext: (Boolean) -> Unit) {
    val options = remember(country) {
        // Prefer look-alike distractors from the same region, then fill from anywhere.
        val sameRegion = data.countries.filter { it.region == country.region && it != country }.shuffled()
        val others = (pool + data.countries).distinct().filter { it != country && it !in sameRegion }.shuffled()
        (listOf(country) + (sameRegion.take(2) + others).take(3)).shuffled()
    }
    var picked by remember(country) { mutableStateOf<Country?>(null) }

    LaunchedEffect(picked) {
        if (picked == country) { delay(900); onNext(true) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlagImage(data, country, Modifier.fillMaxWidth())
        Text("Which country is this?", style = MaterialTheme.typography.titleLarge)
        options.forEach { option ->
            val color = when {
                picked == null -> MaterialTheme.colorScheme.primary
                option == country -> Correct
                option == picked -> Wrong
                else -> Color.Gray
            }
            Button(
                onClick = { if (picked == null) picked = option },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color),
            ) {
                Text(option.displayName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        val p = picked
        if (p != null && p != country) {
            Feedback(false, country) { onNext(false) }
        }
    }
}
