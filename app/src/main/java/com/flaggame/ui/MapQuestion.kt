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
import androidx.compose.ui.draw.clipToBounds
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
import com.flaggame.data.FlagSet
import com.flaggame.data.GameData
import com.flaggame.data.Place
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val Ocean = Color(0xFFAFD4EE)
private val Land = Color(0xFFF2EAD3)
private val Border = Color(0xFF8A7F6A)
private val CapitalMark = Color(0xFF1B4F9C)

private fun ringsToPath(rings: List<FloatArray>) = Path().apply {
    for (r in rings) {
        moveTo(r[0], r[1])
        var i = 2
        while (i < r.size) { lineTo(r[i], r[i + 1]); i += 2 }
        close()
    }
}

@Composable
fun MapQuestion(data: GameData, place: Place, config: GameConfig, pool: List<Place>, onNext: (Boolean) -> Unit) {
    val set = place.set
    val map = remember(set) { data.map(set) }
    val places = remember(set) { data.places(set) }
    val paths = remember(set) { map.shapes.mapValues { ringsToPath(it.value) } }
    val otherPath = remember(set) { ringsToPath(map.other) }
    val capitals = config.quiz == Quiz.CAPITALS
    // Places too small to see or tap get a dot.
    val dots = remember(set) {
        places.filter { it.small || it.code !in map.shapes }.associate { it.code to Offset(it.x, it.y) }
    }
    val density = LocalDensity.current
    val tolerancePx = with(density) { 22.dp.toPx() }
    val dotPx = with(density) { 3.5.dp.toPx() }
    val left = map.bounds[0]; val top = map.bounds[1]
    val mapW = map.bounds[2] - left; val mapH = map.bounds[3] - top

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }   // screen px per map unit, 0 = not laid out yet
    var offset by remember { mutableStateOf(Offset.Zero) }
    var tapped by remember(place) { mutableStateOf<Offset?>(null) }   // in map coordinates
    var tappedCode by remember(place) { mutableStateOf<String?>(null) }
    var correct by remember(place) { mutableStateOf<Boolean?>(null) }

    fun minScale() = if (size.width == 0) 1f else min(size.width / mapW, size.height / mapH)
    fun clamp() {
        val w = mapW * scale; val h = mapH * scale
        val x = if (w <= size.width) (size.width - w) / 2 else offset.x.coerceIn(size.width - w, 0f)
        val y = if (h <= size.height) (size.height - h) / 2 else offset.y.coerceIn(size.height - h, 0f)
        offset = Offset(x, y)
    }
    fun toMap(p: Offset) = Offset((p.x - offset.x) / scale + left, (p.y - offset.y) / scale + top)
    fun toScreen(p: Offset) = Offset((p.x - left) * scale + offset.x, (p.y - top) * scale + offset.y)
    // viewHeight: the answer panel takes part of the map's height once it appears.
    fun zoomTo(minX: Float, minY: Float, maxX: Float, maxY: Float, pad: Float, viewHeight: Float = size.height.toFloat()) {
        val w = (maxX - minX) + pad * 2; val h = (maxY - minY) + pad * 2
        scale = min(size.width / w, viewHeight / h).coerceIn(minScale(), minScale() * 12)
        val cx = (minX + maxX) / 2 - left; val cy = (minY + maxY) / 2 - top
        offset = Offset(size.width / 2 - cx * scale, viewHeight / 2 - cy * scale)
        clamp()
    }
    val panelPx = with(density) { 190.dp.toPx() }
    val pad = min(mapW, mapH) / 15

    fun answer(screen: Offset) {
        if (correct != null) return
        val p = toMap(screen)
        tapped = p
        tappedCode = map.placeAt(p.x, p.y)
            ?: dots.minByOrNull { (_, d) -> hypot(d.x - p.x, d.y - p.y) }
                ?.takeIf { (_, d) -> hypot(d.x - p.x, d.y - p.y) * scale < tolerancePx }?.key
        val target: FloatArray
        val ok: Boolean
        if (capitals) {
            val dist = hypot(place.capitalX - p.x, place.capitalY - p.y)
            ok = dist * scale <= tolerancePx * 1.3f || dist <= map.capitalTolerance
            target = floatArrayOf(place.capitalX, place.capitalY, place.capitalX, place.capitalY)
        } else {
            val dot = dots[place.code]
            val distPx = min(
                map.distanceTo(place.code, p.x, p.y) * scale,
                dot?.let { hypot(it.x - p.x, it.y - p.y) * scale } ?: Float.MAX_VALUE,
            )
            ok = distPx <= tolerancePx
            target = map.bounds(place.code) ?: floatArrayOf(place.x, place.y, place.x, place.y)
        }
        correct = ok
        if (!ok) {
            // Show both the tap and the right answer.
            zoomTo(min(target[0], p.x), min(target[1], p.y), max(target[2], p.x), max(target[3], p.y), pad,
                (size.height - panelPx).coerceAtLeast(size.height / 3f))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlagImage(data, place, Modifier.width(110.dp))
            Text(
                when {
                    correct != null -> ""
                    capitals -> "Tap ${place.displayCapital}, the capital of ${place.label}."
                    else -> "Tap this ${place.kind} on the map. Pinch to zoom."
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged {
                    size = it
                    if (scale == 0f) {
                        scale = minScale()
                        clamp()
                        when {
                            set == FlagSet.COUNTRIES || set == FlagSet.TERRITORIES -> {
                                val pts = if (config.group == ALL) emptyList() else pool
                                if (pts.isEmpty()) {
                                    // The whole world is a thin strip in portrait, so start a bit closer.
                                    zoomTo(-60f, -70f, 80f, 45f, 0f)
                                } else {
                                    zoomTo(pts.minOf { p -> p.x }, pts.minOf { p -> p.y }, pts.maxOf { p -> p.x }, pts.maxOf { p -> p.y }, 8f)
                                }
                            }
                        }
                    }
                }
                .pointerInput(place) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale(), minScale() * 40)
                        val k = newScale / scale
                        offset = (offset - centroid) * k + centroid + pan
                        scale = newScale
                        clamp()
                    }
                }
                .pointerInput(place) { detectTapGestures(onTap = { answer(it) }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Ocean)
                if (scale == 0f) return@Canvas
                val s = scale
                withTransform({
                    translate(offset.x - left * s, offset.y - top * s)
                    scale(s, s, Offset.Zero)
                }) {
                    val hair = Stroke(width = 0.8f / s)
                    drawPath(otherPath, Land.copy(alpha = 0.6f))
                    drawPath(otherPath, Border, style = hair)
                    paths.forEach { (code, path) ->
                        val fill = when {
                            correct != null && !capitals && code == place.code -> Correct
                            correct != null && capitals && code == place.code -> Correct.copy(alpha = 0.35f)
                            correct == false && !capitals && code == tappedCode -> Wrong
                            else -> Land
                        }
                        drawPath(path, fill)
                        drawPath(path, Border, style = hair)
                    }
                }
                if (!capitals) {
                    dots.forEach { (code, d) ->
                        val c = toScreen(d)
                        val color = when {
                            correct != null && code == place.code -> Correct
                            correct == false && code == tappedCode -> Wrong
                            else -> Border
                        }
                        val r = if (correct != null && code == place.code) dotPx * 2 else dotPx
                        drawCircle(color, r, c)
                        drawCircle(Color.White, r, c, style = Stroke(1.5f))
                    }
                }
                if (capitals && correct != null) {
                    val c = toScreen(Offset(place.capitalX, place.capitalY))
                    drawCircle(Correct, dotPx * 2, c)
                    drawCircle(Color.White, dotPx * 2, c, style = Stroke(2f))
                }
                tapped?.let { t ->
                    drawCircle(Color.Black, dotPx * 1.6f, toScreen(t), style = Stroke(3f))
                }
            }
        }
        correct?.let { ok ->
            Column(Modifier.padding(16.dp)) {
                val extra = when {
                    ok -> null
                    capitals -> tappedCode?.let { code -> places.firstOrNull { it.code == code } }
                        ?.let { "You tapped in ${it.label}." }
                    else -> tappedCode?.let { code -> places.firstOrNull { it.code == code } }
                        ?.let { "You tapped ${it.label}." }
                }
                Feedback(ok, answerOf(place, config.quiz), extra) { onNext(ok) }
            }
        }
    }
}
