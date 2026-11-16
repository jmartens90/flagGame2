package com.flaggame.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flaggame.data.GameData
import com.flaggame.data.Place

/** Must match PALETTE in tools/build_assets.py: the template images are drawn in exactly these colours. */
val Palette = listOf(
    "Red" to Color(0xFFD52B1E),
    "Maroon" to Color(0xFF8D1B3D),
    "Orange" to Color(0xFFFF8C00),
    "Yellow" to Color(0xFFFCD116),
    "Green" to Color(0xFF009A44),
    "Light blue" to Color(0xFF75AADB),
    "Blue" to Color(0xFF0038A8),
    "White" to Color(0xFFFFFFFF),
    "Black" to Color(0xFF000000),
)

private const val BLANK = 0xFFE4E4E4.toInt()
private const val OUTLINE = 0xFF505050.toInt()

/** Per-pixel palette index of a flag's colouring template. */
private class Template(bitmap: Bitmap) {
    val w = bitmap.width
    val h = bitmap.height
    val labels: IntArray
    val edge: BooleanArray
    val regions: List<Int>
    private val area: IntArray

    init {
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val lookup = Palette.mapIndexed { i, (_, c) -> (c.toArgb() and 0xFFFFFF) to i }.toMap()
        labels = IntArray(px.size) { lookup[px[it] and 0xFFFFFF] ?: 7 }
        edge = BooleanArray(px.size) { i ->
            val x = i % w; val y = i / w
            (x + 1 < w && labels[i + 1] != labels[i]) || (y + 1 < h && labels[i + w] != labels[i])
        }
        regions = labels.distinct().sorted()
        area = IntArray(Palette.size).also { a -> labels.forEach { a[it]++ } }
    }


    /** Region under a tap; thin or tiny parts (stars, emblems) are easier to hit than their pixels suggest. */
    fun regionAt(x: Int, y: Int): Int {
        val r = 7
        var best = labels[y * w + x]
        var bestD = Int.MAX_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            val xx = x + dx; val yy = y + dy
            if (xx !in 0 until w || yy !in 0 until h) continue
            val l = labels[yy * w + xx]
            val d = dx * dx + dy * dy
            if (area[l] < labels.size / 25 && d < bestD && d <= r * r) { best = l; bestD = d }
        }
        return best
    }

    fun render(fills: Map<Int, Int>, wrong: Set<Int>): Bitmap {
        val out = IntArray(labels.size)
        for (i in labels.indices) {
            val l = labels[i]
            out[i] = when {
                edge[i] -> OUTLINE
                else -> fills[l]?.let { Palette[it].second.toArgb() } ?: BLANK
            }
            // Hatch the regions that were coloured wrong.
            if (l in wrong && ((i % w) + (i / w)) % 12 < 2) out[i] = OUTLINE
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorQuestion(data: GameData, place: Place, onNext: (Boolean) -> Unit) {
    val template = remember(place) { Template(data.colorTemplate(place)) }
    val fills = remember(place) { mutableStateMapOf<Int, Int>() }
    var selected by remember(place) { mutableStateOf(0) }
    var checked by remember(place) { mutableStateOf(false) }

    val wrong = if (checked) template.regions.filter { fills[it] != it }.toSet() else emptySet()
    val image = remember(fills.toMap(), wrong) { template.render(fills, wrong).asImageBitmap() }
    val allFilled = template.regions.all { it in fills }

    // Everything fits on one screen. The flag takes whatever room is left; on wide screens
    // (tablets, landscape) the palette and buttons sit beside it.
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight
        val flagArea: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZoomableFlag(template.w.toFloat() / template.h, enabled = !checked, onTap = { fx, fy ->
                        val x = (fx * template.w).toInt().coerceIn(0, template.w - 1)
                        val y = (fy * template.h).toInt().coerceIn(0, template.h - 1)
                        fills[template.regionAt(x, y)] = selected
                    }) {
                        Image(
                            bitmap = image,
                            contentDescription = "Flag to colour",
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (checked && wrong.isNotEmpty()) {
                    Text("The real flag:", style = MaterialTheme.typography.titleMedium)
                    FlagImage(data, place, Modifier.weight(1f).fillMaxWidth(), fitSpace = true)
                }
            }
        }
        val controls: @Composable () -> Unit = {
            if (!checked) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Palette.forEachIndexed { i, (name, color) ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (i == selected) 4.dp else 1.dp,
                                    if (i == selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    CircleShape,
                                )
                                .clickable { selected = i }
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == selected) {
                                Icon(Icons.Filled.Check, null, tint = if (name in setOf("White", "Yellow", "Light blue")) Color.Black else Color.White)
                            }
                        }
                    }
                }
                Button(onClick = { checked = true }, enabled = allFilled, modifier = Modifier.fillMaxWidth()) {
                    Text(if (allFilled) "Check" else "Colour every part to check")
                }
            } else {
                val ok = wrong.isEmpty()
                Feedback(
                    ok, place.label,
                    if (ok) null else "${template.regions.size - wrong.size} of ${template.regions.size} colours right. Striped parts were wrong.",
                ) { onNext(ok) }
            }
        }
        val title: @Composable () -> Unit = {
            Text("Colour the flag of ${place.label}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (!checked) {
                Text(
                    "Pick a colour, then tap the flag. Pinch to zoom in, double tap to zoom out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                flagArea(Modifier.weight(1.4f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) { title(); controls() }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                title()
                flagArea(Modifier.weight(1f).fillMaxWidth())
                controls()
            }
        }
    }
}

/**
 * The flag being coloured, with pinch-to-zoom, drag to pan and +/- buttons.
 * Taps are reported as fractions (0..1) of the flag's width and height.
 */
@Composable
private fun ZoomableFlag(aspect: Float, enabled: Boolean, onTap: (Float, Float) -> Unit, content: @Composable () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp() {
        zoom = zoom.coerceIn(1f, 6f)
        val maxX = size.width * (zoom - 1); val maxY = size.height * (zoom - 1)
        pan = Offset(pan.x.coerceIn(-maxX, 0f), pan.y.coerceIn(-maxY, 0f))
    }
    fun zoomBy(k: Float, center: Offset = Offset(size.width / 2f, size.height / 2f)) {
        val old = zoom
        zoom = (zoom * k).coerceIn(1f, 6f)
        pan = (pan - center) * (zoom / old) + center
        clamp()
    }
    Box(
        Modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, p, z, _ ->
                    zoomBy(z, centroid)
                    pan += p
                    clamp()
                }
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                    onTap = { p ->
                        if (enabled) onTap((p.x - pan.x) / (size.width * zoom), (p.y - pan.y) / (size.height * zoom))
                    },
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) { content() }
        Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallFloatingActionButton(onClick = { zoomBy(1 / 1.6f) }) { Icon(Icons.Filled.ZoomOut, "Zoom out") }
            SmallFloatingActionButton(onClick = { zoomBy(1.6f) }) { Icon(Icons.Filled.ZoomIn, "Zoom in") }
        }
    }
}
