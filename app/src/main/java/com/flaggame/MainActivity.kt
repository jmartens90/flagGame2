package com.flaggame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.flaggame.data.GameData
import com.flaggame.data.Scores
import com.flaggame.ui.FlagGameTheme
import com.flaggame.ui.GameConfig
import com.flaggame.ui.GameResult
import com.flaggame.ui.GameScreen
import com.flaggame.ui.HomeScreen
import com.flaggame.ui.ResultsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlagGameTheme {
                val context = LocalContext.current
                val data = remember { GameData.get(context) }
                val scores = remember { Scores(context) }
                var config by remember { mutableStateOf<GameConfig?>(null) }
                var result by remember { mutableStateOf<GameResult?>(null) }
                var round by rememberSaveable { mutableStateOf(0) }

                val r = result
                val c = config
                when {
                    r != null -> {
                        BackHandler { result = null; config = null }
                        ResultsScreen(
                            data = data,
                            result = r,
                            onPlayAgain = { result = null; round++ },
                            onHome = { result = null; config = null },
                        )
                    }
                    c != null -> {
                        BackHandler { config = null }
                        GameScreen(
                            data = data,
                            config = c,
                            round = round,
                            onQuit = { config = null },
                            onFinished = { answers ->
                                val pct = if (answers.isEmpty()) 0 else answers.count { it.correct } * 100 / answers.size
                                val newBest = scores.record(c.scoreKey, pct)
                                result = GameResult(c, answers, pct, newBest)
                            },
                        )
                    }
                    else -> HomeScreen(
                        data = data,
                        scores = scores,
                        onStart = { config = it; round++ },
                    )
                }
            }
        }
    }
}
