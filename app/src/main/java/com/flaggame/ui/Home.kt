package com.flaggame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flaggame.data.FlagSet
import com.flaggame.data.GameData
import com.flaggame.data.Scores

enum class Mode(val title: String, val description: String, val capitals: String, val icon: ImageVector) {
    CHOICE("Multiple choice", "Pick the right answer from four options", "Pick the capital from four options", Icons.Filled.Quiz),
    TYPE("Type the answer", "Type or say the name", "Type or say the capital", Icons.Filled.Keyboard),
    MAP("Find it on the map", "Tap the place on the map", "Tap the capital on the map", Icons.Filled.Place),
    COLOR("Colour the flag", "Paint the flag in its real colours", "Only for flags", Icons.Filled.Brush),
}

enum class Quiz(val title: String) { FLAGS("Flags"), CAPITALS("Capitals") }

const val ALL = "All"

data class GameConfig(val set: FlagSet, val quiz: Quiz, val mode: Mode, val group: String, val length: Int) {
    val scoreKey get() = "${set.id}|${quiz.name}|${mode.name}|$group"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(data: GameData, scores: Scores, onStart: (GameConfig) -> Unit) {
    var set by rememberSaveable { mutableStateOf(FlagSet.COUNTRIES) }
    var quiz by rememberSaveable { mutableStateOf(Quiz.FLAGS) }
    var group by rememberSaveable { mutableStateOf(ALL) }
    var length by rememberSaveable { mutableStateOf(10) }
    val groups = listOf(ALL) + data.groups(set)
    if (group !in groups) group = ALL
    val count = data.places(set).count {
        (group == ALL || it.group == group) && (quiz == Quiz.FLAGS || it.capital != null)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Flag Game", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)

            Section("Flags of")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlagSet.entries.forEach { s ->
                    FilterChip(selected = set == s, onClick = { set = s; group = ALL }, label = { Text(s.title) })
                }
            }
            Section(if (set == FlagSet.BELGIUM) "Show" else "Region")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                }
            }
            Section("Quiz")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quiz.entries.forEach { q ->
                    FilterChip(selected = quiz == q, onClick = { quiz = q }, label = { Text(q.title) })
                }
            }
            Section("Questions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10 to "10", 20 to "20", 0 to "All ($count)").forEach { (n, label) ->
                    FilterChip(selected = length == n, onClick = { length = n }, label = { Text(label) })
                }
            }

            Section("Game")
            Mode.entries.forEach { mode ->
                val enabled = count > 0 && !(mode == Mode.COLOR && quiz == Quiz.CAPITALS)
                val config = GameConfig(set, quiz, mode, group, length)
                ElevatedCard(
                    onClick = { onStart(config) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(mode.icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (quiz == Quiz.CAPITALS) mode.capitals else mode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val best = scores.best(config.scoreKey)
                        if (enabled && best >= 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Best", style = MaterialTheme.typography.labelSmall)
                                Text("$best%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text(
                "Flags: flag-icons (MIT), region-flags and Wikimedia Commons (public domain, CC0, CC BY-SA). " +
                    "Maps: Natural Earth (public domain), US Census Bureau, © EuroGeographics for the Belgian provinces. " +
                    "Names: world-countries (ODbL), capitals located with GeoNames (CC BY 4.0).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}
