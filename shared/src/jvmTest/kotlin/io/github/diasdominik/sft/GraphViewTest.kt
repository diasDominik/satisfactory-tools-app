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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.swipe
import io.github.diasdominik.sft.planner.GraphPoint
import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionMetadata
import io.github.diasdominik.sft.planner.SftFormat
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

    @Test
    fun draggingANodeMovesIt() = runComposeUiTest {
        val moves = mutableListOf<Pair<String, Offset>>()
        setContent { SatisfactoryTheme { GraphView(nodes, edges, onMoveNode = { key, at -> moves += key to at }) } }
        onNodeWithContentDescription("Fit graph").performClick()
        val graph = onNodeWithContentDescription("Production graph")
        val zoom = graph.zoom
        // The middle node (Iron Ingot) sits in the centre; drag it to the right.
        graph.performTouchInput { swipe(center, center + Offset(200f, 0f), durationMillis = 300) }
        val (key, at) = moves.single()
        assertEquals("2", key)
        assertTrue(at.x > 40f, "moved to $at")
        // Moving a node is not a zoom.
        assertEquals(zoom, graph.zoom)
    }

    @Test
    fun draggingEmptySpaceMovesTheViewNotANode() = runComposeUiTest {
        val moves = mutableListOf<String>()
        setContent { SatisfactoryTheme { GraphView(nodes, edges, onMoveNode = { key, _ -> moves += key }) } }
        onNodeWithContentDescription("Production graph").performTouchInput { swipe(Offset(5f, 5f), Offset(150f, 150f), durationMillis = 300) }
        assertTrue(moves.isEmpty(), "moved $moves")
    }

    @Test
    fun aNodeCanBeMarkedAsBuilt() = runComposeUiTest {
        var steps by mutableStateOf(nodes.map { it.copy(checkable = true) })
        val toggled = mutableListOf<String>()
        setContent {
            SatisfactoryTheme {
                GraphView(steps, edges, onToggleDone = { key ->
                    toggled += key
                    steps = steps.map { if (it.key == key) it.copy(done = !it.done) else it }
                })
            }
        }
        onNodeWithText("0 of 3 steps built").assertIsDisplayed()
        onNodeWithContentDescription("Fit graph").performClick()
        onNodeWithContentDescription("Production graph").performTouchInput { click(center) }
        onNodeWithText("Built").performClick()
        assertEquals(listOf("2"), toggled)
        onNodeWithText("1 of 3 steps built").assertIsDisplayed()
        // The details stay open after marking, so it can be undone right away.
        onNodeWithText("Needed power: 4 MW").assertIsDisplayed()
    }

    @Test
    fun builtMarksAndPositionsSurviveExport() {
        val data = ProductionData(
            metadata = ProductionMetadata(built = listOf("Recipe_IronRod_C"), graphPositions = mapOf("mine:Desc_OreIron_C" to GraphPoint(10f, 20f))),
        )
        val back = SftFormat.import(SftFormat.export(listOf(data), listOf("Rods"))).single().metadata
        assertEquals(listOf("Recipe_IronRod_C"), back.built)
        assertEquals(GraphPoint(10f, 20f), back.graphPositions["mine:Desc_OreIron_C"])
    }
}
