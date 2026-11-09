package com.flaggame.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flaggame.data.Country
import com.flaggame.data.GameData

data class Answer(val country: Country, val correct: Boolean)

data class GameResult(val config: GameConfig, val answers: List<Answer>, val percent: Int, val newBest: Boolean)

fun pool(data: GameData, config: GameConfig): List<Country> =
    data.countries
        .filter { config.region == WORLD || it.region == config.region }
        .filter { config.mode != Mode.COLOR || it.colorable }

@Composable
fun GameScreen(
    data: GameData,
    config: GameConfig,
    round: Int,
    onQuit: () -> Unit,
    onFinished: (List<Answer>) -> Unit,
) {
    val questions = remember(config, round) {
        val all = pool(data, config).shuffled()
        if (config.length > 0) all.take(config.length) else all
    }
    val answers = remember(config, round) { mutableStateListOf<Answer>() }
    var index by remember(config, round) { mutableIntStateOf(0) }

    val next: (Boolean) -> Unit = { correct ->
        answers += Answer(questions[index], correct)
        if (index + 1 >= questions.size) onFinished(answers.toList()) else index++
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQuit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quit") }
                Text(config.mode.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${answers.count { it.correct }} ✓   ${index + 1} / ${questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            LinearProgressIndicator(
                progress = { index.toFloat() / questions.size },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (questions.isEmpty()) {
                Text("No flags to play here.", Modifier.padding(24.dp))
                return@Column
            }
            val country = questions[index]
            key(round, index) {
                Box(Modifier.weight(1f)) {
                    when (config.mode) {
                        Mode.CHOICE -> MultipleChoiceQuestion(data, country, pool(data, config), next)
                        Mode.TYPE -> TypeAnswerQuestion(data, country, next)
                        Mode.MAP -> MapQuestion(data, country, config.region, next)
                        Mode.COLOR -> ColorQuestion(data, country, next)
                    }
                }
            }
        }
    }
}

@Composable
fun FlagImage(data: GameData, country: Country, modifier: Modifier = Modifier) {
    val bitmap = remember(country.code) { data.flag(country.code).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = "Flag",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
    )
}

/** "Correct!" / "It's X" line plus a Next button, shown after answering. */
@Composable
fun Feedback(correct: Boolean, country: Country, extra: String? = null, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (correct) "Correct! ${country.displayName}" else "It's ${country.displayName}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (correct) Correct else Wrong,
            textAlign = TextAlign.Center,
        )
        if (extra != null) {
            Text(extra, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
    }
}

@Composable
fun ResultsScreen(data: GameData, result: GameResult, onPlayAgain: () -> Unit, onHome: () -> Unit) {
    val right = result.answers.count { it.correct }
    val wrong = result.answers.filter { !it.correct }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(result.config.mode.title, style = MaterialTheme.typography.titleMedium)
            Text("${result.percent}%", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Text("$right of ${result.answers.size} right", style = MaterialTheme.typography.titleLarge)
            if (result.newBest) {
                Text("New best for ${result.config.region}!", color = Correct, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            if (wrong.isNotEmpty()) {
                Text("Worth another look", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(wrong, key = { it.country.code }) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(data, a.country, Modifier.width(64.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.country.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text("Perfect round!", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f)) { Text("Home") }
                Button(onClick = onPlayAgain, modifier = Modifier.weight(1f)) { Text("Play again") }
            }
        }
    }
}
