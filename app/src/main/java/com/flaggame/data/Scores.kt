package com.flaggame.data

import android.content.Context

/** Best scores per game setup, kept on the device. */
class Scores(context: Context) {
    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun best(key: String): Int = prefs.getInt(key, -1)

    /** Saves the score as a percentage; returns true if it is a new best. */
    fun record(key: String, percent: Int): Boolean {
        if (percent > best(key)) {
            prefs.edit().putInt(key, percent).apply()
            return true
        }
        return false
    }
}
