package com.flaggame.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flaggame.data.GameData
import com.flaggame.data.Place
import kotlinx.coroutines.delay

@Composable
fun TypeAnswerQuestion(data: GameData, place: Place, quiz: Quiz, onNext: (Boolean) -> Unit) {
    val all = data.places(place.set)
    val check: (String) -> Boolean = { if (quiz == Quiz.CAPITALS) place.matchesCapital(it, all) else place.matches(it, all) }
    val context = LocalContext.current
    var text by remember(place) { mutableStateOf("") }
    var result by remember(place) { mutableStateOf<Boolean?>(null) }
    var tries by remember(place) { mutableStateOf(0) }
    var hint by remember(place) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    fun submit(candidates: List<String>) {
        if (result != null) return
        if (candidates.any { check(it) }) {
            result = true
        } else {
            tries++
            hint = if (tries < 2) "Not quite, try once more." else null
            if (tries >= 2) result = false
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if (heard.isNotEmpty()) {
                text = heard.firstOrNull { check(it) } ?: heard.first()
                submit(heard)
            }
        }
    }

    LaunchedEffect(result) {
        if (result == true) { delay(900); onNext(true) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlagImage(data, place, Modifier.fillMaxWidth())
        Text(questionText(place, quiz), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; hint = null },
                label = { Text(if (quiz == Quiz.CAPITALS) "Capital" else place.kind.replaceFirstChar { it.uppercase() }) },
                singleLine = true,
                enabled = result == null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit(listOf(text)) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (voiceAvailable) {
                FilledTonalIconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, if (quiz == Quiz.CAPITALS) "Say the capital" else "Say the ${place.kind}")
                        runCatching { speech.launch(intent) }
                    },
                    enabled = result == null,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Icon(Icons.Filled.Mic, "Answer by voice") }
            }
        }
        hint?.let { Text(it, color = Wrong) }
        when (result) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { result = false }, modifier = Modifier.weight(1f)) { Text("I don't know") }
                Button(onClick = { submit(listOf(text)) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("Check")
                }
            }
            true -> Feedback(true, answerOf(place, quiz)) { onNext(true) }
            false -> Feedback(false, answerOf(place, quiz)) { onNext(false) }
        }
    }
}
