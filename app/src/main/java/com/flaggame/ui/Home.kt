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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flaggame.data.GameData
import com.flaggame.data.Scores

enum class Mode(val title: String, val description: String, val icon: ImageVector) {
    CHOICE("Multiple choice", "Pick the right country from four options", Icons.Filled.Quiz),
    TYPE("Type the answer", "Type or say the country's name", Icons.Filled.Keyboard),
    MAP("Find it on the map", "Tap the country on the world map", Icons.Filled.Place),
    COLOR("Colour the flag", "Paint the flag in its real colours", Icons.Filled.Brush),
}

const val WORLD = "World"

data class GameConfig(val mode: Mode, val region: String, val length: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(data: GameData, scores: Scores, onStart: (GameConfig) -> Unit) {
    var region by rememberSaveable { mutableStateOf(WORLD) }
    var length by rememberSaveable { mutableStateOf(10) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Flag Game", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "${data.countries.size} countries of the world",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Region", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(WORLD) + data.regions).forEach { r ->
                    FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r) })
                }
            }
            Text("Questions", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10 to "10", 20 to "20", 0 to "All").forEach { (n, label) ->
                    FilterChip(selected = length == n, onClick = { length = n }, label = { Text(label) })
                }
            }

            Text("Game", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Mode.entries.forEach { mode ->
                ElevatedCard(
                    onClick = { onStart(GameConfig(mode, region, length)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(mode.icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.title, style = MaterialTheme.typography.titleLarge)
                            Text(mode.description, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val best = scores.best(mode.name, region)
                        if (best >= 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Best", style = MaterialTheme.typography.labelSmall)
                                Text("$best%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text(
                "Flags: flag-icons (MIT). Map: Natural Earth (public domain). Country names: world-countries (ODbL).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
