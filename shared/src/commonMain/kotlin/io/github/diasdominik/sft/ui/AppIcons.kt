package io.github.diasdominik.sft.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own line icons, drawn on a 24×24 grid with a 2-unit round stroke.
 * Paths starting with `*` are filled instead of stroked.
 */
object AppIcons {
    // Actions
    val Add by lazy { icon("Add", "M12 5v14M5 12h14") }
    val Remove by lazy { icon("Remove", "M5 12h14") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Search by lazy { icon("Search", "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13zM15.5 15.5L20 20") }
    val Share by lazy {
        icon(
            "Share",
            circle(18f, 5f, 2.5f), circle(6f, 12f, 2.5f), circle(18f, 19f, 2.5f),
            "M8.2 10.8l7.6-4.6M8.2 13.2l7.6 4.6",
        )
    }
    val Delete by lazy { icon("Delete", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6") }
    val Copy by lazy { icon("Copy", "M9 9h11v11H9zM5 15H4V4h11v1") }
    val Edit by lazy { icon("Edit", "M4 20h4L19 9l-4-4L4 16zM13 7l4 4") }
    val Image by lazy { icon("Image", "M4 5h16v14H4zM4 16l5-5 4 4 2-2 5 5", dot(15.5f, 9f)) }
    val Reset by lazy { icon("Reset", "M4 12a8 8 0 1 0 2.4-5.7M4 4v4h4") }
    val SelectAll by lazy { icon("SelectAll", "M4 4h16v16H4zM8 12l3 3 5-6") }
    val Import by lazy { icon("Import", "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val Filter by lazy { icon("Filter", "M4 7h16M7 12h10M10 17h4") }
    val Fit by lazy { icon("Fit", "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5", circle(12f, 12f, 3f)) }
    val Info by lazy { icon("Info", circle(12f, 12f, 9f), "M12 11v6", dot(12f, 7.5f, 1.2f)) }
    val MoreVert by lazy { icon("MoreVert", dot(12f, 5f, 1.7f), dot(12f, 12f, 1.7f), dot(12f, 19f, 1.7f)) }

    // Navigation
    val ArrowBack by lazy { icon("ArrowBack", "M19 12H5M11 6l-6 6 6 6", autoMirror = true) }
    val ArrowForward by lazy { icon("ArrowForward", "M5 12h14M13 6l6 6-6 6", autoMirror = true) }
    val ArrowUp by lazy { icon("ArrowUp", "M12 19V5M6 11l6-6 6 6") }
    val ArrowDown by lazy { icon("ArrowDown", "M12 5v14M6 13l6 6 6-6") }
    val ExpandMore by lazy { icon("ExpandMore", "M6 9l6 6 6-6") }
    val ExpandLess by lazy { icon("ExpandLess", "M6 15l6-6 6 6") }
    val List by lazy {
        icon("List", "M9 6h11M9 12h11M9 18h11", dot(4.5f, 6f), dot(4.5f, 12f), dot(4.5f, 18f), autoMirror = true)
    }

    // Sections, buildings and schematics
    val Factory by lazy { icon("Factory", "M3 21V11l5 3v-3l5 3V4h4v17zM3 21h18") }
    val Items by lazy { icon("Items", "M12 3l4 7H8z", circle(7f, 17f, 3.5f), "M14 14h7v7h-7z") }
    val Flask by lazy { icon("Flask", "M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4a2 2 0 0 0 1.8-3l-5-9V3M7.5 15h9") }
    val HardDrive by lazy { icon("HardDrive", "M4 6h16v12H4zM4 14h16", dot(16.5f, 16.2f, 1f), dot(13.5f, 16.2f, 1f)) }
    val Bolt by lazy { icon("Bolt", "M13 2L4 14h7l-1 8 9-12h-7z") }
    val Drill by lazy { icon("Drill", "M8 3h8v5l-2 3v4l-2 6-2-6v-4l-2-3zM8 8h8") }
    val Drop by lazy { icon("Drop", "M12 3c3.5 4.5 6 7.7 6 11a6 6 0 0 1-12 0c0-3.3 2.5-6.5 6-11z") }
    val Pipe by lazy { icon("Pipe", "M3 8h9a6 6 0 0 1 6 6v7M3 14h6a3 3 0 0 1 3 3v4M3 6v10M16 21h4") }
    val Conveyor by lazy {
        icon("Conveyor", "M6 8h12a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8z", dot(6f, 12f), dot(12f, 12f), dot(18f, 12f))
    }
    val Train by lazy {
        icon("Train", "M6 4h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM4 11h16M8 21l2-4M16 21l-2-4")
    }
    val Plug by lazy { icon("Plug", "M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4") }
    val Box by lazy { icon("Box", "M3 7l9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4M12 11v10") }
    val Truck by lazy {
        icon("Truck", "M2 6h11v10H2zM13 10h4l3 3v3h-7", circle(6f, 18f, 2f), circle(17f, 18f, 2f))
    }
    val Lightbulb by lazy {
        icon("Lightbulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")
    }
    val Wall by lazy { icon("Wall", "M3 5h18v14H3zM3 12h18M9 5v7M15 12v7") }
    val Foundation by lazy { icon("Foundation", "M3 10h18l-2 8H5zM3 10l3-4h12l3 4") }
    val Gear by lazy {
        icon(
            "Gear",
            circle(12f, 12f, 3f), circle(12f, 12f, 7f),
            "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1",
        )
    }
    val Building by lazy { icon("Building", "M4 21V8l8-5 8 5v13zM9 21v-6h6v6M2 21h20") }
    val Flag by lazy { icon("Flag", "M5 21V4M5 4h12l-2 4 2 4H5") }
    val Shuffle by lazy {
        icon("Shuffle", "M4 7h3c5 0 5 10 10 10h3M4 17h3c2 0 3-1.5 4-3.5M13 9.5C14 8 15 7 17 7h3M17 4l3 3-3 3M17 14l3 3-3 3")
    }
    val Store by lazy { icon("Store", "M4 10v10h16V10M3 10l2-6h14l2 6zM10 20v-5h4v5") }
    val Disc by lazy { icon("Disc", circle(12f, 12f, 9f), circle(12f, 12f, 2f)) }
    val Book by lazy { icon("Book", "M4 19V5a2 2 0 0 1 2-2h13v14H6a2 2 0 0 0-2 2a2 2 0 0 0 2 2h13") }
    val Palette by lazy {
        icon(
            "Palette",
            "M12 3a9 9 0 0 0 0 18c1.1 0 2-.9 2-2s.9-2 2-2h2a3 3 0 0 0 3-3c0-6-4-11-9-11z",
            dot(7.5f, 11f), dot(10f, 7f), dot(14.5f, 7.5f),
        )
    }
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun dot(cx: Float, cy: Float, r: Float = 1.5f) = "*" + circle(cx, cy, r)

private fun icon(name: String, vararg paths: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        val ink = SolidColor(Color.Black)
        for (path in paths) {
            if (path.startsWith("*")) {
                addPath(addPathNodes(path.drop(1)), fill = ink)
            } else {
                addPath(
                    addPathNodes(path),
                    stroke = ink,
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()
