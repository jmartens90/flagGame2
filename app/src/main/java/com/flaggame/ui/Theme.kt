package com.flaggame.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Correct = Color(0xFF2E9E4F)
val Wrong = Color(0xFFD64545)

@Composable
fun FlagGameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9CC2FF), secondary = Color(0xFFFFD45C))
        else -> lightColorScheme(primary = Color(0xFF1B4F9C), secondary = Color(0xFFB88A00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
