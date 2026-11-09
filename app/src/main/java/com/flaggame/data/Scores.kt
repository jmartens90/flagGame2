package com.flaggame.data

import android.content.Context

/** Best scores per mode and region, kept on the device. */
class Scores(context: Context) {
    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun best(mode: String, region: String): Int = prefs.getInt("$mode|$region", -1)

    /** Saves the score as a percentage; returns true if it is a new best. */
    fun record(mode: String, region: String, percent: Int): Boolean {
        val old = best(mode, region)
        if (percent > old) {
            prefs.edit().putInt("$mode|$region", percent).apply()
            return true
        }
        return false
    }
}
