package com.flaggame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

data class Country(
    val code: String,
    val englishName: String,
    val altNames: List<String>,
    val region: String,
    val lat: Double,
    val lng: Double,
    val areaKm2: Double,
    val colorable: Boolean,
) {
    /** Name in the phone's language, falling back to English. */
    val displayName: String by lazy {
        val local = Locale("", code.uppercase()).getDisplayCountry(Locale.getDefault())
        if (local.isBlank() || local.equals(code, ignoreCase = true) || Locale.getDefault().language == "en") englishName else local
    }

    private val accepted: Set<String> by lazy {
        buildSet {
            add(normalize(englishName))
            add(normalize(displayName))
            add(normalize(Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH)))
            altNames.forEach { add(normalize(it)) }
            remove("")
        }
    }

    /** Exact (normalised) match against any accepted name. */
    fun matchesExactly(answer: String): Boolean = normalize(answer) in accepted

    /**
     * Lenient answer check: ignores case, accents and punctuation, tolerates small typos.
     * An answer that is exactly another country's name (Zambia for Gambia) is never accepted.
     */
    fun matches(answer: String, all: List<Country> = emptyList()): Boolean {
        val a = normalize(answer)
        if (a.isEmpty()) return false
        if (a in accepted) return true
        if (all.any { it != this && it.matchesExactly(answer) }) return false
        for (name in accepted) {
            if (a == name) return true
            val allowed = when {
                name.length >= 10 -> 2
                name.length >= 5 -> 1
                else -> 0
            }
            if (allowed > 0 && levenshtein(a, name) <= allowed) return true
        }
        return false
    }

    companion object {
        fun normalize(s: String): String {
            val stripped = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace("&", " and ")
                .replace("saint ", "st ")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
            return stripped.removePrefix("the ").trim()
        }

        /** Edit distance where swapping two neighbouring letters counts as one typo. */
        fun levenshtein(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
            return d[a.length][b.length]
        }
    }
}

/** Loads bundled game data from assets. All of it is small, so it is cached for the app's lifetime. */
class GameData(private val context: Context) {
    val countries: List<Country> by lazy { loadCountries() }
    val world: WorldMap by lazy { WorldMap.load(context) }
    private val flagCache = HashMap<String, Bitmap>()

    val regions: List<String> get() = countries.map { it.region }.distinct().sorted()

    fun flag(code: String): Bitmap = synchronized(flagCache) {
        flagCache.getOrPut(code) {
            context.assets.open("flags/$code.webp").use { BitmapFactory.decodeStream(it) }
        }
    }

    fun colorTemplate(code: String): Bitmap =
        context.assets.open("color/$code.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!
        }

    private fun loadCountries(): List<Country> {
        val text = context.assets.open("countries.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val alt = o.getJSONArray("alt")
            Country(
                code = o.getString("code"),
                englishName = o.getString("name"),
                altNames = (0 until alt.length()).map { alt.getString(it) },
                region = o.getString("region"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                areaKm2 = o.optDouble("area", 0.0),
                colorable = o.getBoolean("colorable"),
            )
        }
    }

    companion object {
        @Volatile private var instance: GameData? = null
        fun get(context: Context): GameData =
            instance ?: synchronized(this) { instance ?: GameData(context.applicationContext).also { instance = it } }
    }
}
