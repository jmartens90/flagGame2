package com.flaggame.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Country outlines in an equirectangular projection: x = longitude, y = -latitude (degrees).
 * Source: Natural Earth 1:50m via the world-atlas package, simplified at build time.
 */
class WorldMap(
    val shapes: Map<String, List<FloatArray>>,
    val other: List<FloatArray>,
) {
    /** The country whose outline contains the point, if any. */
    fun countryAt(x: Float, y: Float): String? =
        shapes.entries.firstOrNull { (_, rings) -> rings.any { contains(it, x, y) } }?.key

    /** Distance (in degrees) from the point to the nearest edge of the country, 0 if inside. */
    fun distanceTo(code: String, x: Float, y: Float): Float {
        val rings = shapes[code] ?: return Float.MAX_VALUE
        if (rings.any { contains(it, x, y) }) return 0f
        var best = Float.MAX_VALUE
        for (r in rings) {
            var i = 0
            while (i < r.size) {
                val j = (i + 2) % r.size
                best = minOf(best, segDist(x, y, r[i], r[i + 1], r[j], r[j + 1]))
                i += 2
            }
        }
        return best
    }

    fun bounds(code: String): FloatArray? {
        val rings = shapes[code] ?: return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (r in rings) {
            var i = 0
            while (i < r.size) {
                minX = minOf(minX, r[i]); maxX = maxOf(maxX, r[i])
                minY = minOf(minY, r[i + 1]); maxY = maxOf(maxY, r[i + 1])
                i += 2
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    companion object {
        fun load(context: Context): WorldMap {
            val o = JSONObject(context.assets.open("world.json").bufferedReader().use { it.readText() })
            val scale = o.getDouble("scale").toFloat()
            fun rings(a: JSONArray) = (0 until a.length()).map { i ->
                val r = a.getJSONArray(i)
                FloatArray(r.length()) { r.getInt(it) / scale }
            }
            val c = o.getJSONObject("countries")
            val shapes = c.keys().asSequence().associateWith { rings(c.getJSONArray(it)) }
            return WorldMap(shapes, rings(o.getJSONArray("other")))
        }

        fun contains(r: FloatArray, x: Float, y: Float): Boolean {
            var inside = false
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i]; val yi = r[2 * i + 1]
                val xj = r[2 * j]; val yj = r[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val len = dx * dx + dy * dy
            val t = if (len == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0f, 1f)
            val qx = ax + t * dx - px; val qy = ay + t * dy - py
            return kotlin.math.sqrt(qx * qx + qy * qy)
        }
    }
}
