package io.github.diasdominik.sft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class GraphViewNode(
    val id: Int,
    val title: String,
    val subtitle: String = "",
    val color: Color,
    val details: List<String> = emptyList(),
    val action: Pair<String, () -> Unit>? = null,
)

data class GraphViewEdge(val from: Int, val to: Int, val label: String = "")

private const val NODE_WIDTH = 190f
private const val NODE_HEIGHT = 64f
private const val H_GAP = 40f
private const val V_GAP = 90f

private const val MIN_SCALE = 0.1f
private const val MAX_SCALE = 4f
private const val BUTTON_ZOOM = 1.4f
private const val WHEEL_ZOOM = 1.15f

private class Layout(val positions: Map<Int, Offset>, val bounds: Rect)

/**
 * Layered (Sugiyama-style) layout: longest-path layering, then a few barycenter sweeps to reduce
 * crossings. Good enough for production chains and schematic trees.
 */
private fun layout(nodes: List<GraphViewNode>, edges: List<GraphViewEdge>): Layout {
    if (nodes.isEmpty()) return Layout(emptyMap(), Rect.Zero)
    val ids = nodes.map { it.id }
    val outgoing = ids.associateWith { mutableListOf<Int>() }
    val incoming = ids.associateWith { mutableListOf<Int>() }
    // Drop back edges (cycles) found by DFS so layering terminates.
    val state = HashMap<Int, Int>()
    val forward = mutableListOf<GraphViewEdge>()
    val edgesFrom = edges.filter { it.from != it.to && it.from in outgoing && it.to in outgoing }.groupBy { it.from }
    fun dfs(n: Int) {
        state[n] = 1
        for (e in edgesFrom[n].orEmpty()) {
            when (state[e.to]) {
                null -> { forward += e; dfs(e.to) }
                2 -> forward += e
                else -> {} // back edge
            }
        }
        state[n] = 2
    }
    ids.forEach { if (state[it] == null) dfs(it) }
    forward.distinctBy { it.from to it.to }.forEach {
        outgoing.getValue(it.from) += it.to
        incoming.getValue(it.to) += it.from
    }

    val layer = HashMap<Int, Int>()
    fun layerOf(n: Int): Int = layer[n] ?: (incoming.getValue(n).maxOfOrNull { layerOf(it) + 1 } ?: 0).also { layer[n] = it }
    ids.forEach { layerOf(it) }

    val layers = ids.groupBy { layer.getValue(it) }.entries.sortedBy { it.key }.map { it.value.toMutableList() }
    val order = HashMap<Int, Double>()
    fun reindex() = layers.forEach { l -> l.forEachIndexed { i, n -> order[n] = i.toDouble() } }
    reindex()
    repeat(4) { sweep ->
        val range = if (sweep % 2 == 0) layers.indices.drop(1) else layers.indices.reversed().drop(1)
        for (li in range) {
            val neighbours = if (sweep % 2 == 0) incoming else outgoing
            layers[li].sortBy { n ->
                val ns = neighbours.getValue(n)
                if (ns.isEmpty()) order.getValue(n) else ns.sumOf { order.getValue(it) } / ns.size
            }
            layers[li].forEachIndexed { i, n -> order[n] = i.toDouble() }
        }
    }

    val widest = layers.maxOf { it.size }
    val totalWidth = widest * (NODE_WIDTH + H_GAP)
    val positions = HashMap<Int, Offset>()
    layers.forEachIndexed { li, l ->
        val rowWidth = l.size * (NODE_WIDTH + H_GAP)
        val start = (totalWidth - rowWidth) / 2
        l.forEachIndexed { i, n ->
            positions[n] = Offset(start + i * (NODE_WIDTH + H_GAP) + H_GAP / 2, li * (NODE_HEIGHT + V_GAP))
        }
    }
    return Layout(positions, Rect(0f, 0f, totalWidth, layers.size * (NODE_HEIGHT + V_GAP) - V_GAP))
}

@Composable
fun GraphView(
    nodes: List<GraphViewNode>,
    edges: List<GraphViewEdge>,
    modifier: Modifier = Modifier,
    height: Dp = 480.dp,
    highlight: Int? = null,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val graphLayout = remember(nodes, edges, density) {
        val l = layout(nodes, edges)
        Layout(l.positions.mapValues { it.value * density }, Rect(0f, 0f, l.bounds.width * density, l.bounds.height * density))
    }
    val nodeWidth = NODE_WIDTH * density
    val nodeHeight = NODE_HEIGHT * density
    val measurer = rememberTextMeasurer()
    var scale by remember(graphLayout) { mutableStateOf(1f) }
    var offset by remember(graphLayout) { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember(graphLayout) { mutableStateOf(false) }
    var selected by remember(graphLayout) { mutableStateOf<Int?>(null) }
    val nodesById = remember(nodes) { nodes.associateBy { it.id } }

    fun fit() {
        if (viewSize.width == 0) return
        val b = graphLayout.bounds
        val margin = 24f * density
        val s = min(viewSize.width / (b.width + margin), viewSize.height / (b.height + margin)).coerceIn(MIN_SCALE, 1.5f)
        scale = s
        offset = Offset(
            (viewSize.width - b.width * s) / 2,
            max(margin / 2, (viewSize.height - b.height * s) / 2),
        )
    }
    /** Zooms by [factor] keeping the graph point under [pivot] in place. */
    fun zoomBy(factor: Float, pivot: Offset) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = newScale / scale
        offset = pivot - (pivot - offset) * applied
        scale = newScale
    }
    fun nodeAt(point: Offset): Int? {
        val p = (point - offset) / scale
        return graphLayout.positions.entries.firstOrNull { (_, pos) ->
            p.x in pos.x..(pos.x + nodeWidth) && p.y in pos.y..(pos.y + nodeHeight)
        }?.key
    }
    LaunchedEffect(graphLayout, viewSize) {
        if (!fitted && viewSize.width > 0) {
            fit()
            fitted = true
        }
    }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(height)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                .onSizeChanged { viewSize = it },
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .semantics {
                        contentDescription = "Production graph"
                        stateDescription = "Zoom ${(scale * 100).roundToInt()} %"
                    }
                    .pointerInput(graphLayout) {
                        // One gesture handler for drag, pinch and taps. It consumes every touch inside the
                        // graph so the surrounding list doesn't scroll away while panning or pinching.
                        var lastTapTime = 0L
                        var lastTapNode: Int? = null
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var moved = 0f
                            var multiTouch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size > 1) multiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                moved += pan.getDistance()
                                if (zoom != 1f) zoomBy(zoom, event.calculateCentroid(useCurrent = true))
                                if (moved > viewConfiguration.touchSlop || multiTouch) offset += pan
                                event.changes.forEach { it.consume() }
                            }
                            if (!multiTouch && moved <= viewConfiguration.touchSlop) {
                                val hit = nodeAt(down.position)
                                val now = down.uptimeMillis
                                if (hit != null && hit == lastTapNode && now - lastTapTime < viewConfiguration.doubleTapTimeoutMillis) {
                                    nodesById[hit]?.action?.second?.invoke()
                                    lastTapNode = null
                                } else {
                                    selected = hit
                                    lastTapNode = hit
                                    lastTapTime = now
                                }
                            }
                        }
                    }
                    .pointerInput(graphLayout) {
                        // Mouse wheel and trackpad scrolling zoom on desktop.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val delta = change.scrollDelta.y
                                if (delta != 0f) {
                                    zoomBy(WHEEL_ZOOM.pow(-delta), change.position)
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    val d = density
                    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
                    for (edge in edges) {
                        val a = graphLayout.positions[edge.from] ?: continue
                        val b = graphLayout.positions[edge.to] ?: continue
                        val start = Offset(a.x + nodeWidth / 2, a.y + nodeHeight)
                        val end = Offset(b.x + nodeWidth / 2, b.y)
                        val path = Path()
                        val upward = end.y <= start.y
                        path.moveTo(start.x, start.y)
                        if (upward) {
                            val bend = 60f * d
                            path.cubicTo(start.x + bend * 2, start.y + bend, end.x + bend * 2, end.y - bend, end.x, end.y)
                        } else {
                            val dy = (end.y - start.y) / 2
                            path.cubicTo(start.x, start.y + dy, end.x, end.y - dy, end.x, end.y)
                        }
                        val emphasised = selected != null && (edge.from == selected || edge.to == selected)
                        drawPath(path, if (emphasised) highlightColor else lineColor, style = Stroke(width = (if (emphasised) 2.5f else 1.2f) * d))
                        // Arrow head
                        val arrow = Path().apply {
                            moveTo(end.x, end.y)
                            lineTo(end.x - 5f * d, end.y - 9f * d)
                            lineTo(end.x + 5f * d, end.y - 9f * d)
                            close()
                        }
                        drawPath(arrow, if (emphasised) highlightColor else lineColor)
                        if (edge.label.isNotEmpty()) {
                            val mid = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
                            val measured = measurer.measure(edge.label, labelStyle, maxLines = 2)
                            val w = measured.size.width.toFloat()
                            val h = measured.size.height.toFloat()
                            val topLeft = Offset(mid.x - w / 2, mid.y - h / 2)
                            drawRoundRect(labelBackground, topLeft - Offset(3f * d, 1f * d), Size(w + 6f * d, h + 2f * d), CornerRadius(4f * d))
                            drawText(measured, topLeft = topLeft)
                        }
                    }
                    for (node in nodes) {
                        val pos = graphLayout.positions[node.id] ?: continue
                        drawRoundRect(node.color, pos, Size(nodeWidth, nodeHeight), CornerRadius(8f * d))
                        if (node.id == selected || node.id == highlight) {
                            drawRoundRect(highlightColor, pos, Size(nodeWidth, nodeHeight), CornerRadius(8f * d), style = Stroke(2.5f * d))
                        }
                        val textColor = if (node.color.red * 0.299f + node.color.green * 0.587f + node.color.blue * 0.114f > 0.6f) Color.Black else Color.White
                        drawText(
                            measurer, node.title, pos + Offset(6f * d, 6f * d),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            size = Size(nodeWidth - 12f * d, 30f * d),
                        )
                        if (node.subtitle.isNotEmpty()) {
                            drawText(
                                measurer, node.subtitle, pos + Offset(6f * d, 36f * d),
                                style = TextStyle(fontSize = 10.sp, color = textColor, textAlign = TextAlign.Center),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                size = Size(nodeWidth - 12f * d, 26f * d),
                            )
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                FilledTonalIconButton(onClick = { zoomBy(BUTTON_ZOOM, center) }) { Icon(AppIcons.Add, contentDescription = "Zoom in") }
                FilledTonalIconButton(onClick = { zoomBy(1 / BUTTON_ZOOM, center) }) { Icon(AppIcons.Remove, contentDescription = "Zoom out") }
                FilledTonalIconButton(onClick = { fit() }) { Icon(AppIcons.Fit, contentDescription = "Fit graph") }
            }
        }
        val node = selected?.let { nodesById[it] }
        if (node != null) {
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(node.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        node.action?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                    }
                    if (node.subtitle.isNotEmpty()) Text(node.subtitle, style = MaterialTheme.typography.bodySmall)
                    node.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else {
            Text(
                "Pinch, scroll or use + and − to zoom, drag to move, tap a node for details, double-tap to open it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
