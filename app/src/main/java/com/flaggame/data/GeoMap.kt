package com.flaggame.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Outlines for the map mode, already projected at build time (y points down).
 * World: x = longitude, y = -latitude. US: Albers USA. Belgium: scaled longitude / latitude.
 */
class GeoMap(
    val shapes: Map<String, List<FloatArray>>,
    val other: List<FloatArray>,
    /** minX, minY, maxX, maxY of the area worth showing. */
    val bounds: FloatArray,
    /** How far (in map units) from a capital a tap still counts. */
    val capitalTolerance: Float,
) {
    fun placeAt(x: Float, y: Float): String? =
        shapes.entries.firstOrNull { (_, rings) -> rings.any { contains(it, x, y) } }?.key

    /** Distance (in map units) from the point to the nearest edge of the shape, 0 if inside. */
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
        fun parse(text: String): GeoMap {
            val o = JSONObject(text)
            val scale = o.getDouble("scale").toFloat()
            fun rings(a: JSONArray) = (0 until a.length()).map { i ->
                val r = a.getJSONArray(i)
                FloatArray(r.length()) { r.getInt(it) / scale }
            }
            val s = o.getJSONObject("shapes")
            val b = o.getJSONArray("bounds")
            return GeoMap(
                shapes = s.keys().asSequence().associateWith { rings(s.getJSONArray(it)) },
                other = rings(o.getJSONArray("other")),
                bounds = FloatArray(4) { b.getDouble(it).toFloat() },
                capitalTolerance = o.optDouble("capTol", 0.0).toFloat(),
            )
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
            return sqrt(qx * qx + qy * qy)
        }
    }
}
