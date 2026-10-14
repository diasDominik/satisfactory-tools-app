package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.ConnectorKind
import io.github.diasdominik.sft.planner.FactoryLayout
import io.github.diasdominik.sft.ui.AppIcons
import kotlin.math.min
import kotlin.math.pow

internal val BeltColor = Color(0xFF8A9BB0)
internal val PipeColor = Color(0xFF3FA7B5)
internal val OverloadColor = Color(0xFFE0A100)
internal val SplitterColor = Color(0xFF4A90D9)
internal val MergerColor = Color(0xFFB065D8)
private val MachineColor = Color(0xFFDF691A)
private val RowColor = Color(0x14FFFFFF)
private val GridColor = Color(0x22FFFFFF)

/** Pixels per metre at zoom 1, in dp. */
private const val METRE = 5f

/** Top-down floor plan of a [FactoryLayout], with pinch, drag and wheel zoom like the graph. */
@Composable
internal fun FactoryLayoutView(data: GameData, plan: FactoryLayout, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    var scale by remember(plan) { mutableStateOf(1f) }
    var offset by remember(plan) { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = 14.0
    val minX = plan.left - margin
    val maxX = plan.width + 60.0
    val minY = -margin
    val maxY = plan.length + margin

    fun fit() {
        if (viewSize.width == 0) return
        val w = ((maxX - minX) * METRE * density).toFloat()
        val h = ((maxY - minY) * METRE * density).toFloat()
        scale = min(viewSize.width / w, viewSize.height / h).coerceIn(0.05f, 2f)
        offset = Offset((-minX * METRE * density * scale).toFloat(), (-minY * METRE * density * scale).toFloat())
    }
    fun zoomBy(factor: Float, pivot: Offset) {
        val target = (scale * factor).coerceIn(0.05f, 6f)
        offset = pivot - (pivot - offset) * (target / scale)
        scale = target
    }
    LaunchedEffect(plan, viewSize) { fit() }

    Box(
        modifier.fillMaxWidth().height(520.dp).clipToBounds()
            .background(Color(0xFF1C2024), RoundedCornerShape(12.dp))
            .onSizeChanged { viewSize = it },
    ) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Factory layout" }
                .pointerInput(plan) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) zoomBy(zoom, event.calculateCentroid(useCurrent = true))
                            offset += event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .pointerInput(plan) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.scrollDelta.y != 0f) {
                                zoomBy(1.15f.pow(-change.scrollDelta.y), change.position)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val m = METRE * density
            fun p(x: Double, y: Double) = Offset((x * m).toFloat(), (y * m).toFloat())
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // Foundation grid.
                val f = FactoryLayout.FOUNDATION
                var gx = plan.left - (plan.left % f + f) % f
                while (gx <= plan.width) {
                    drawLine(GridColor, p(gx, 0.0), p(gx, plan.length), strokeWidth = 1f)
                    gx += f
                }
                var gy = 0.0
                while (gy <= plan.length) {
                    drawLine(GridColor, p(plan.left, gy), p(plan.width, gy), strokeWidth = 1f)
                    gy += f
                }
                val beltWidth = 1.4f * m
                // Bus.
                for (belt in plan.belts) {
                    val color = if (belt.lines > 1) OverloadColor else if (belt.fluid) PipeColor else BeltColor
                    drawLine(color, p(belt.laneX, belt.top), p(belt.laneX, belt.bottom), strokeWidth = beltWidth)
                    (belt.fromYs + belt.toYs).forEach { y -> drawLine(color, p(belt.laneX, y), p(0.0, y), strokeWidth = beltWidth) }
                    if (belt.fromYs.isEmpty()) label(measurer, data.nameOf(belt.item), p(belt.laneX, belt.top) - Offset(0f, 2f * m), m, rotate = true)
                    if (belt.toYs.isEmpty()) label(measurer, data.nameOf(belt.item), p(belt.laneX, belt.bottom) + Offset(0f, 0.5f * m), m, rotate = true)
                }
                // Rows.
                for (row in plan.rows) {
                    drawRect(RowColor, p(0.0, row.y), Size((row.width * m).toFloat(), (row.depth * m).toFloat()))
                    for (lane in row.lanes) {
                        drawLine(if (lane.fluid) PipeColor else BeltColor, p(0.0, lane.y), p(row.width, lane.y), strokeWidth = beltWidth)
                    }
                    for (i in 0 until row.count) {
                        val x = row.machineX(i) - row.machineWidth / 2
                        drawRect(MachineColor, p(x, row.machineY), Size((row.machineWidth * m).toFloat(), (row.machineLength * m).toFloat()))
                        // Feed and output connections between building and belts.
                        for (lane in row.lanes) {
                            val end = if (lane.input) row.machineY else row.machineY + row.machineLength
                            drawLine(if (lane.fluid) PipeColor else BeltColor, p(row.machineX(i), lane.y), p(row.machineX(i), end), strokeWidth = beltWidth * 0.6f)
                        }
                    }
                    val name = data.recipe(row.recipe)?.name ?: row.recipe
                    label(measurer, "${row.count}× ${data.nameOf(row.machine)} · $name", p(row.width + 2, row.y + row.depth / 2), m)
                }
                for (c in plan.connectors) {
                    val center = p(c.x, c.y)
                    val s = 1.6f * m
                    when (c.kind) {
                        ConnectorKind.SPLITTER -> drawRect(SplitterColor, center - Offset(s / 2, s / 2), Size(s, s))
                        ConnectorKind.MERGER -> drawRect(MergerColor, center - Offset(s / 2, s / 2), Size(s, s))
                        ConnectorKind.JUNCTION -> drawCircle(PipeColor, s * 0.7f, center, style = Stroke(0.5f * m))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
            FilledTonalIconButton(onClick = { zoomBy(1.4f, center) }) { Icon(AppIcons.Add, contentDescription = "Zoom in") }
            FilledTonalIconButton(onClick = { zoomBy(1 / 1.4f, center) }) { Icon(AppIcons.Remove, contentDescription = "Zoom out") }
            FilledTonalIconButton(onClick = { fit() }) { Icon(AppIcons.Fit, contentDescription = "Fit layout") }
        }
    }
}

private fun DrawScope.label(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    metre: Float,
    rotate: Boolean = false,
) {
    val measured = measurer.measure(text, TextStyle(fontSize = 11.sp, color = Color(0xFFE6E6E6), fontWeight = FontWeight.Medium))
    val h = measured.size.height.toFloat()
    val w = measured.size.width.toFloat()
    if (rotate) {
        withTransform({ rotate(-90f, at) }) {
            drawRect(Color(0xAA1C2024), at - Offset(0f, h / 2), Size(w, h))
            drawText(measured, topLeft = at - Offset(0f, h / 2))
        }
    } else {
        drawRect(Color(0xAA1C2024), at - Offset(0f, h / 2), Size(w, h))
        drawText(measured, topLeft = at - Offset(0f, h / 2))
    }
}
