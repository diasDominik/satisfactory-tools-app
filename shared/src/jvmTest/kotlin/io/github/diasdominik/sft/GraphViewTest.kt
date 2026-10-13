package io.github.diasdominik.sft

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.ui.GraphView
import io.github.diasdominik.sft.ui.GraphViewEdge
import io.github.diasdominik.sft.ui.GraphViewNode
import io.github.diasdominik.sft.ui.SatisfactoryTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the production graph with real touch and mouse input. */
@OptIn(ExperimentalTestApi::class)
class GraphViewTest {
    private val nodes = listOf(
        GraphViewNode(1, "Iron Ore", "Mine 30 / min", Color(0xFF8E8E8E)),
        GraphViewNode(2, "Iron Ingot", "1× Smelter", Color(0xFFDF691A), details = listOf("Needed power: 4 MW")),
        GraphViewNode(3, "Iron Plate", "2× Constructor", Color(0xFF4E9F3D)),
    )
    private val edges = listOf(GraphViewEdge(1, 2, "30"), GraphViewEdge(2, 3, "30"))

    private val SemanticsNodeInteraction.zoom: Int
        get() = fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix(" %").toInt()

    @Test
    fun zoomButtonsChangeTheZoom() = runComposeUiTest {
        setContent { SatisfactoryTheme { GraphView(nodes, edges) } }
        val graph = onNodeWithContentDescription("Production graph")
        val fitted = graph.zoom
        onNodeWithContentDescription("Zoom in").performClick()
        assertTrue(graph.zoom > fitted, "zoom in: ${graph.zoom} after $fitted")
        onNodeWithContentDescription("Zoom out").performClick()
        onNodeWithContentDescription("Zoom out").performClick()
        assertTrue(graph.zoom < fitted, "zoom out: ${graph.zoom} after $fitted")
        onNodeWithContentDescription("Fit graph").performClick()
        assertEquals(fitted, graph.zoom)
    }

    @Test
    fun pinchZooms() = runComposeUiTest {
        setContent { SatisfactoryTheme { GraphView(nodes, edges) } }
        val graph = onNodeWithContentDescription("Production graph")
        val before = graph.zoom
        graph.performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(150f, 0f), center + Offset(20f, 0f), center + Offset(150f, 0f))
        }
        assertTrue(graph.zoom > before, "pinch out: ${graph.zoom} after $before")
        val zoomed = graph.zoom
        graph.performTouchInput {
            pinch(center - Offset(150f, 0f), center - Offset(20f, 0f), center + Offset(150f, 0f), center + Offset(20f, 0f))
        }
        assertTrue(graph.zoom < zoomed, "pinch in: ${graph.zoom} after $zoomed")
    }

    @Test
    fun mouseWheelZooms() = runComposeUiTest {
        setContent { SatisfactoryTheme { GraphView(nodes, edges) } }
        val graph = onNodeWithContentDescription("Production graph")
        val before = graph.zoom
        graph.performMouseInput { scroll(-3f) }
        assertTrue(graph.zoom > before, "wheel up: ${graph.zoom} after $before")
    }

    @Test
    fun tappingANodeShowsItsDetails() = runComposeUiTest {
        setContent { SatisfactoryTheme { GraphView(nodes, edges) } }
        onNodeWithContentDescription("Fit graph").performClick()
        // Tap where the middle node is drawn: the layout stacks the three nodes in the horizontal centre.
        onNodeWithContentDescription("Production graph").performTouchInput { click(center) }
        onNodeWithText("Needed power: 4 MW").assertIsDisplayed()
    }

    @Test
    fun draggingTheGraphDoesNotScrollThePage() = runComposeUiTest {
        var listState: androidx.compose.foundation.lazy.LazyListState? = null
        setContent {
            SatisfactoryTheme {
                val state = rememberLazyListState().also { listState = it }
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    item { GraphView(nodes, edges, height = 400.dp) }
                    items(30) { Text("Row $it") }
                }
            }
        }
        onNodeWithContentDescription("Production graph").performTouchInput { swipeUp() }
        waitForIdle()
        assertEquals(0, listState!!.firstVisibleItemIndex)
        assertEquals(0, listState!!.firstVisibleItemScrollOffset)
    }
}
