package com.flaggame.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** A collection of flags that can be played: its assets live in assets/<id>/. */
enum class FlagSet(val id: String, val title: String, val noun: String, val usesIsoCodes: Boolean) {
    COUNTRIES("countries", "Countries", "country", true),
    TERRITORIES("territories", "Territories", "territory", true),
    US("us", "US states", "state", false),
    BELGIUM("belgium", "Belgium", "province", false),
}

/** One flag: a country, territory, state, province or city. Map coordinates are in the set's map projection. */
data class Place(
    val set: FlagSet,
    val code: String,
    val englishName: String,
    val names: Map<String, String>,
    val altNames: List<String>,
    val group: String,
    val x: Float,
    val y: Float,
    val small: Boolean,
    val colorable: Boolean,
    val capital: String?,
    val capitalNames: Map<String, String>,
    val capitalAlt: List<String>,
    val capitalX: Float,
    val capitalY: Float,
    /** Another place in the set has the same name (Liège the city and Liège the province). */
    val qualified: Boolean = false,
) {
    private val lang get() = Locale.getDefault().language

    /** Name in the phone's language where known, otherwise English. */
    val displayName: String by lazy {
        names[lang]
            ?: if (set.usesIsoCodes && lang != "en") {
                Locale("", code.uppercase()).getDisplayCountry(Locale.getDefault())
                    .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: englishName
            } else englishName
    }

    val displayCapital: String? by lazy { capital?.let { capitalNames[lang] ?: it } }

    /** Name with "(city)" or "(province)" added where two places share a name. */
    val label: String get() = if (qualified) "$displayName ($kind)" else displayName

    /** What this place is called in questions: "country", "state", "city", ... */
    val kind: String get() = if (set == FlagSet.BELGIUM && group == "Cities") "city" else set.noun

    private val accepted: Set<String> by lazy {
        buildSet {
            add(normalize(englishName))
            add(normalize(displayName))
            names.values.forEach { add(normalize(it)) }
            if (set.usesIsoCodes) add(normalize(Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH)))
            altNames.forEach { add(normalize(it)) }
            remove("")
        }
    }

    private val acceptedCapital: Set<String> by lazy {
        buildSet {
            capital?.let { add(normalize(it)) }
            capitalNames.values.forEach { add(normalize(it)) }
            capitalAlt.forEach { add(normalize(it)) }
            remove("")
        }
    }

    fun matchesExactly(answer: String) = normalize(answer) in accepted
    fun matchesCapitalExactly(answer: String) = normalize(answer) in acceptedCapital

    /**
     * Lenient answer check: ignores case, accents and punctuation, tolerates small typos.
     * An answer that is exactly another place's name (Zambia for Gambia) is never accepted.
     */
    fun matches(answer: String, all: List<Place> = emptyList()) =
        fuzzy(answer, accepted) { other -> other.matchesExactly(answer) }.let { it(all) }

    fun matchesCapital(answer: String, all: List<Place> = emptyList()) =
        fuzzy(answer, acceptedCapital) { other -> other.matchesCapitalExactly(answer) }.let { it(all) }

    private fun fuzzy(answer: String, names: Set<String>, isOther: (Place) -> Boolean): (List<Place>) -> Boolean = { all ->
        val a = normalize(answer)
        when {
            a.isEmpty() -> false
            a in names -> true
            all.any { it != this && isOther(it) } -> false
            else -> names.any { name ->
                val allowed = when {
                    name.length >= 10 -> 2
                    name.length >= 5 -> 1
                    else -> 0
                }
                allowed > 0 && levenshtein(a, name) <= allowed
            }
        }
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

        fun fromJson(set: FlagSet, o: JSONObject): Place {
            fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            fun map(key: String) = o.optJSONObject(key)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
            return Place(
                set = set,
                code = o.getString("code"),
                englishName = o.getString("name"),
                names = map("names"),
                altNames = strings("alt"),
                group = o.getString("group"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                small = o.optBoolean("small"),
                colorable = o.optBoolean("colorable"),
                capital = o.optString("capital").ifEmpty { null },
                capitalNames = map("capitalNames"),
                capitalAlt = strings("capitalAlt"),
                capitalX = o.optDouble("cx", 0.0).toFloat(),
                capitalY = o.optDouble("cy", 0.0).toFloat(),
            )
        }
    }
}

/** Loads bundled game data from assets, cached for the app's lifetime. */
class GameData(private val context: Context) {
    private val places = HashMap<FlagSet, List<Place>>()
    private val maps = HashMap<FlagSet, GeoMap>()
    private val flagCache = HashMap<String, Bitmap>()

    fun places(set: FlagSet): List<Place> = synchronized(places) {
        places.getOrPut(set) {
            val arr = JSONArray(read("${set.id}/items.json"))
            val list = (0 until arr.length()).map { Place.fromJson(set, arr.getJSONObject(it)) }
            val shared = list.groupBy { it.englishName }.filterValues { it.size > 1 }.keys
            list.map { if (it.englishName in shared) it.copy(qualified = true) else it }
        }
    }

    fun map(set: FlagSet): GeoMap = synchronized(maps) { maps.getOrPut(set) { GeoMap.parse(read("${set.id}/map.json")) } }

    fun groups(set: FlagSet): List<String> = places(set).map { it.group }.distinct().sorted()

    fun flag(place: Place): Bitmap = synchronized(flagCache) {
        flagCache.getOrPut("${place.set.id}/${place.code}") {
            context.assets.open("${place.set.id}/flags/${place.code}.webp").use { BitmapFactory.decodeStream(it) }
        }
    }

    fun colorTemplate(place: Place): Bitmap =
        context.assets.open("${place.set.id}/color/${place.code}.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })!!
        }

    private fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        @Volatile private var instance: GameData? = null
        fun get(context: Context): GameData =
            instance ?: synchronized(this) { instance ?: GameData(context.applicationContext).also { instance = it } }
    }
}
