package com.flaggame.data

import android.content.Context
import kotlin.math.pow
import kotlin.random.Random

/**
 * How many times in a row each flag (or capital) was answered right, kept on the device.
 * Learning mode uses it to ask well-known flags less often.
 */
class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    private fun key(place: Place, capitals: Boolean) = "${place.set.id}|${if (capitals) "cap" else "flag"}|${place.code}"

    fun streak(place: Place, capitals: Boolean): Int = prefs.getInt(key(place, capitals), 0)

    /** A right answer adds one to the streak; a wrong one starts it again from zero. */
    fun record(place: Place, capitals: Boolean, correct: Boolean) {
        val k = key(place, capitals)
        prefs.edit().putInt(k, if (correct) prefs.getInt(k, 0) + 1 else 0).apply()
    }

    /** 1 = normal, 0.5 = right 5+ times in a row, 0.1 = right 10+ times in a row. */
    fun weight(place: Place, capitals: Boolean): Double = weightFor(streak(place, capitals))

    /**
     * Picks [count] places without repeats, each with a chance in proportion to its weight
     * (Efraimidis–Spirakis weighted sampling).
     */
    fun pick(places: List<Place>, capitals: Boolean, count: Int, random: Random = Random): List<Place> =
        places.sortedByDescending { random.nextDouble().pow(1.0 / weight(it, capitals)) }.take(count)

    companion object {
        const val HALF = 5
        const val MASTERED = 10
        fun weightFor(streak: Int) = when {
            streak >= MASTERED -> 0.1
            streak >= HALF -> 0.5
            else -> 1.0
        }
    }
}
