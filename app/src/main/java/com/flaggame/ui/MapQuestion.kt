package com.flaggame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flaggame.data.Country
import com.flaggame.data.GameData
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val MAP_LEFT = -180f
private const val MAP_TOP = -84f      // y = -latitude
private const val MAP_W = 360f
private const val MAP_H = 142f        // down to 58°S, Antarctica left out
private const val SMALL_KM2 = 2_500.0 // smaller countries get a dot so they can be seen and tapped

private val Ocean = Color(0xFFAFD4EE)
private val Land = Color(0xFFF2EAD3)
private val Border = Color(0xFF8A7F6A)

private fun ringsToPath(rings: List<FloatArray>) = Path().apply {
    for (r in rings) {
        moveTo(r[0], r[1])
        var i = 2
        while (i < r.size) { lineTo(r[i], r[i + 1]); i += 2 }
        close()
    }
}

@Composable
fun MapQuestion(data: GameData, country: Country, region: String, onNext: (Boolean) -> Unit) {
    val world = data.world
    val paths = remember { world.shapes.mapValues { ringsToPath(it.value) } }
    val otherPath = remember { ringsToPath(world.other) }
    val dots = remember {
        data.countries.filter { it.areaKm2 < SMALL_KM2 || it.code !in world.shapes }
            .associate { it.code to Offset(it.lng.toFloat(), -it.lat.toFloat()) }
    }
    val density = LocalDensity.current
    val tolerancePx = with(density) { 22.dp.toPx() }
    val dotPx = with(density) { 3.5.dp.toPx() }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }   // screen px per degree, 0 = not laid out yet
    var offset by remember { mutableStateOf(Offset.Zero) }
    var tapped by remember(country) { mutableStateOf<Offset?>(null) }   // in map coordinates
    var tappedCode by remember(country) { mutableStateOf<String?>(null) }
    var correct by remember(country) { mutableStateOf<Boolean?>(null) }

    fun minScale() = if (size.width == 0) 1f else size.width / MAP_W
    fun clamp() {
        val s = scale
        val w = MAP_W * s; val h = MAP_H * s
        val x = if (w <= size.width) (size.width - w) / 2 else offset.x.coerceIn(size.width - w, 0f)
        val y = if (h <= size.height) (size.height - h) / 2 else offset.y.coerceIn(size.height - h, 0f)
        offset = Offset(x, y)
    }
    fun toMap(p: Offset) = Offset((p.x - offset.x) / scale + MAP_LEFT, (p.y - offset.y) / scale + MAP_TOP)
    fun zoomTo(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        val pad = 12f
        val w = (maxX - minX) + pad * 2; val h = (maxY - minY) + pad * 2
        scale = min(size.width / w, size.height / h).coerceIn(minScale(), minScale() * 12)
        val cx = (minX + maxX) / 2 - MAP_LEFT; val cy = (minY + maxY) / 2 - MAP_TOP
        offset = Offset(size.width / 2 - cx * scale, size.height / 2 - cy * scale)
        clamp()
    }

    fun answer(screen: Offset) {
        if (correct != null) return
        val p = toMap(screen)
        tapped = p
        val dot = dots[country.code]
        val distPx = min(
            world.distanceTo(country.code, p.x, p.y) * scale,
            dot?.let { hypot(it.x - p.x, it.y - p.y) * scale } ?: Float.MAX_VALUE,
        )
        tappedCode = world.countryAt(p.x, p.y)
            ?: dots.minByOrNull { (_, d) -> hypot(d.x - p.x, d.y - p.y) }
                ?.takeIf { (_, d) -> hypot(d.x - p.x, d.y - p.y) * scale < tolerancePx }?.key
        val ok = distPx <= tolerancePx
        correct = ok
        if (!ok) {
            // Show both the tap and the right answer.
            val b = world.bounds(country.code) ?: dot?.let { floatArrayOf(it.x, it.y, it.x, it.y) }
            if (b != null) zoomTo(min(b[0], p.x), min(b[1], p.y), max(b[2], p.x), max(b[3], p.y))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlagImage(data, country, Modifier.width(110.dp))
            Text(
                if (correct == null) "Tap this country on the map. Pinch to zoom." else "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    size = it
                    if (scale == 0f) {
                        scale = minScale()
                        if (region == WORLD) {
                            // Whole-world width leaves a thin strip in portrait, so start a bit closer.
                            zoomTo(-60f, -70f, 80f, 45f)
                        } else {
                            val pts = data.countries.filter { c -> c.region == region }
                            zoomTo(
                                pts.minOf { c -> c.lng }.toFloat(), pts.minOf { c -> -c.lat }.toFloat(),
                                pts.maxOf { c -> c.lng }.toFloat(), pts.maxOf { c -> -c.lat }.toFloat(),
                            )
                        }
                    }
                }
                .pointerInput(country) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale(), minScale() * 40)
                        val k = newScale / scale
                        offset = (offset - centroid) * k + centroid + pan
                        scale = newScale
                        clamp()
                    }
                }
                .pointerInput(country) { detectTapGestures(onTap = { answer(it) }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Ocean)
                if (scale == 0f) return@Canvas
                val s = scale
                withTransform({
                    translate(offset.x - MAP_LEFT * s, offset.y - MAP_TOP * s)
                    scale(s, s, Offset.Zero)
                }) {
                    val hair = Stroke(width = 0.8f / s)
                    drawPath(otherPath, Land.copy(alpha = 0.7f))
                    drawPath(otherPath, Border, style = hair)
                    paths.forEach { (code, path) ->
                        val fill = when {
                            correct != null && code == country.code -> Correct
                            correct == false && code == tappedCode -> Wrong
                            else -> Land
                        }
                        drawPath(path, fill)
                        drawPath(path, Border, style = hair)
                    }
                }
                // Dots for tiny countries, drawn at constant screen size.
                dots.forEach { (code, d) ->
                    val c = Offset((d.x - MAP_LEFT) * s + offset.x, (d.y - MAP_TOP) * s + offset.y)
                    val color = when {
                        correct != null && code == country.code -> Correct
                        correct == false && code == tappedCode -> Wrong
                        else -> Border
                    }
                    val r = if (correct != null && code == country.code) dotPx * 2 else dotPx
                    drawCircle(color, r, c)
                    drawCircle(Color.White, r, c, style = Stroke(1.5f))
                }
                tapped?.let { t ->
                    val c = Offset((t.x - MAP_LEFT) * s + offset.x, (t.y - MAP_TOP) * s + offset.y)
                    drawCircle(Color.Black, dotPx * 1.6f, c, style = Stroke(3f))
                }
            }
        }
        correct?.let { ok ->
            Column(Modifier.padding(16.dp)) {
                val extra = if (!ok) tappedCode?.let { code ->
                    data.countries.firstOrNull { it.code == code }?.let { "You tapped ${it.displayName}." }
                } else null
                Feedback(ok, country, extra) { onNext(ok) }
            }
        }
    }
}
