package io.github.diasdominik.sft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.github.diasdominik.sft.data.GameData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original, generated icons in the spirit of the game's item art: a shape for the kind of item
 * (ore, ingot, plate, fluid, …) tinted with the colour of its material. The game's own icons are
 * Coffee Stain's and may not be redistributed by derivative works.
 */
enum class Glyph {
    ORE, INGOT, PLATE, SHEET, ROD, WIRE, CABLE, SCREW, BEAM, PIPE, FRAME, GEAR, CHIP, CRYSTAL, POWDER,
    DROP, GAS, CANISTER, FUEL_ROD, CELL, BATTERY, LEAF, REMAINS, AMMO, BOMB, TOOL, ELEVATOR_PART, CUBE,
    COUPON, SPHERE, FILTER, FABRIC, BOX,
}

private val TileTop = Color(0xFF3A4048)
private val TileBottom = Color(0xFF23272C)

private fun materialColor(name: String, className: String): Color {
    val n = name.lowercase()
    val c = className.lowercase()
    return when {
        "ficsonium" in n -> Color(0xFFE04BD0)
        "ficsite" in n -> Color(0xFFE8B04A)
        "plutonium" in n -> Color(0xFF3FC7D9)
        "uranium" in n || "nuclear" in c -> Color(0xFF7BD34F)
        "dark matter" in n || "singularity" in n -> Color(0xFF6B4FD8)
        "sam" in n || "alien" in n || "somersloop" in n || "mercer" in n || "photonic" in n || "time crystal" in n -> Color(0xFFB065E8)
        "caterium" in n || "quickwire" in n || "gold" in n || "ai limiter" in n || "high-speed" in n -> Color(0xFFE8B83A)
        "copper" in n || "wire" in n || "cable" in n -> Color(0xFFD9793A)
        "aluminum" in n || "alclad" in n || "heat sink" in n -> Color(0xFFC9D3DC)
        "bauxite" in n -> Color(0xFFB4553A)
        "steel" in n || "encased industrial" in n || "heavy modular" in n -> Color(0xFF7F8A96)
        "quartz" in n || "silica" in n || "crystal oscillator" in n -> Color(0xFFF2A6C9)
        "sulfur" in n -> Color(0xFFE6D84A)
        "coal" in n || "coke" in n || "black powder" in n -> Color(0xFF4A4A4A)
        "limestone" in n || "concrete" in n -> Color(0xFFD8CDB0)
        "plastic" in n -> Color(0xFF5BA9E6)
        "rubber" in n -> Color(0xFF3B3F45)
        "polymer" in n || "heavy oil" in n || "oil" in n -> Color(0xFF6C3F7A)
        "turbo" in n -> Color(0xFFE85A3A)
        "rocket" in n -> Color(0xFFE8463A)
        "ionized" in n -> Color(0xFFE8E06A)
        "fuel" in n -> Color(0xFFE89A3A)
        "biofuel" in n || "biomass" in n || "leaves" in n || "mycelia" in n || "wood" in n || "fabric" in n -> Color(0xFF7DB84A)
        "water" in n -> Color(0xFF5DA9E8)
        "nitrogen" in n || "nitric" in n -> Color(0xFF9FB7C9)
        "battery" in n -> Color(0xFF4FC3A1)
        "diamond" in n -> Color(0xFFBFF3FF)
        "power slug" in n || "power shard" in n -> when {
            "blue" in n -> Color(0xFF4FA3E8)
            "yellow" in n -> Color(0xFFE8D84A)
            "purple" in n -> Color(0xFFB065E8)
            else -> Color(0xFF4FA3E8)
        }
        "coupon" in n -> Color(0xFFE88A3A)
        "iron" in n || "screw" in n || "rotor" in n || "rebar" in n || "modular frame" in n -> Color(0xFFA9B4C2)
        "computer" in n || "circuit" in n || "radio" in n || "processor" in n -> Color(0xFF4FB86A)
        "motor" in n || "stator" in n -> Color(0xFFB0B8C2)
        else -> Color(0xFFDF8A3E)
    }
}

fun glyphFor(name: String, className: String): Glyph {
    val n = name.lowercase()
    val c = className.lowercase()
    return when {
        c.startsWith("bp_equipment") || c.startsWith("bp_eq") || n in setOf("chainsaw", "parachute", "rebar gun", "portable miner", "medicinal inhaler", "factory cart™", "golden factory cart™") -> Glyph.TOOL
        "spaceelevatorpart" in c -> Glyph.ELEVATOR_PART
        "coupon" in n -> Glyph.COUPON
        "fuel rod" in n -> Glyph.FUEL_ROD
        "cell" in n && "singularity" !in n -> Glyph.CELL
        "battery" in n -> Glyph.BATTERY
        "nobelisk" in n -> Glyph.BOMB
        "ammo" in n || "rebar" in n -> Glyph.AMMO
        "remains" in n || "alien protein" in n || "dna" in n -> Glyph.REMAINS
        "filter" in n -> Glyph.FILTER
        "fabric" in n -> Glyph.FABRIC
        "packaged" in n || "canister" in n || "fluid tank" in n -> Glyph.CANISTER
        n == "nitrogen gas" || "gas" == n -> Glyph.GAS
        "ore" in n || n in setOf("coal", "limestone", "bauxite", "sulfur", "raw quartz", "sam", "uranium") -> Glyph.ORE
        "ingot" in n || n == "reanimated sam" -> Glyph.INGOT
        "sheet" in n -> Glyph.SHEET
        "plate" in n || "plating" in n || n == "heat sink" -> Glyph.PLATE
        "rod" in n -> Glyph.ROD
        "quickwire" in n || n == "wire" -> Glyph.WIRE
        "cable" in n || "wiring" in n -> Glyph.CABLE
        "screw" in n -> Glyph.SCREW
        "beam" in n -> Glyph.BEAM
        "pipe" in n -> Glyph.PIPE
        "frame" in n || "framework" in n -> Glyph.FRAME
        "rotor" in n || "stator" in n || "motor" in n || "engine" in n || "cooling" in n -> Glyph.GEAR
        "computer" in n || "circuit" in n || "limiter" in n || "connector" in n || "control unit" in n || "processor" in n ||
            "oscillator" in n || "server" in n -> Glyph.CHIP
        "crystal" in n || "diamond" in n || "power slug" in n || "power shard" in n || "trigon" in n -> Glyph.CRYSTAL
        "powder" in n || n in setOf("concrete", "silica", "polymer resin", "petroleum coke", "compacted coal", "dark matter residue", "ficsonium") -> Glyph.POWDER
        n in setOf("leaves", "mycelia", "wood", "biomass", "solid biofuel", "bacon agaric", "paleberry", "beryl nut") -> Glyph.LEAF
        "cube" in n -> Glyph.CUBE
        "sphere" in n || "somersloop" in n || "fluctuator" in n || "photonic" in n -> Glyph.SPHERE
        "plastic" in n || "rubber" in n -> Glyph.SHEET
        else -> Glyph.BOX
    }
}

private fun buildingIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "generator" in n || "power storage" in n -> Icons.Default.Bolt
        "miner" in n || "extractor" in n || "oil pump" in n || "pressurizer" in n -> Icons.Default.Construction
        "water" in n -> Icons.Default.Water
        "pipe" in n || "pump" in n || "valve" in n || "fluid" in n -> Icons.Default.Plumbing
        "conveyor" in n || "belt" in n || "lift" in n || "splitter" in n || "merger" in n -> Icons.Default.LinearScale
        "railway" in n || "train" in n || "locomotive" in n || "freight" in n -> Icons.Default.Train
        "power" in n || "pole" in n || "wire" in n || "switch" in n -> Icons.Default.ElectricalServices
        "storage" in n || "container" in n || "tank" in n || "dimensional" in n -> Icons.Default.Inventory2
        "truck" in n || "tractor" in n || "explorer" in n || "vehicle" in n || "drone" in n -> Icons.Default.DirectionsCar
        "light" in n || "sign" in n || "lamp" in n -> Icons.Default.Lightbulb
        "wall" in n || "window" in n || "roof" in n || "ramp" in n || "beam" in n || "pillar" in n || "stair" in n || "frame" in n -> Icons.Default.ViewQuilt
        "foundation" in n || "floor" in n || "walkway" in n || "catwalk" in n -> Icons.Default.Foundation
        listOf("constructor", "assembler", "manufacturer", "smelter", "foundry", "refinery", "blender", "packager", "accelerator", "converter", "encoder").any { it in n } -> Icons.Default.PrecisionManufacturing
        "workshop" in n || "bench" in n || "hub" in n || "elevator" in n || "m.a.m" in n || "sink" in n -> Icons.Default.Factory
        else -> Icons.Default.Domain
    }
}

private fun schematicIcon(type: String): ImageVector = when (type) {
    "EST_Milestone" -> Icons.Default.Flag
    "EST_MAM" -> Icons.Default.Science
    "EST_Alternate" -> Icons.Default.Shuffle
    "EST_ResourceSink" -> Icons.Default.Storefront
    "EST_HardDrive" -> Icons.Default.Album
    "EST_Tutorial" -> Icons.Default.School
    "EST_Customization" -> Icons.Default.Palette
    else -> Icons.Default.Science
}

@Composable
fun GameIcon(data: GameData?, className: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier.size(size).clip(shape).background(Brush.verticalGradient(listOf(TileTop, TileBottom))),
    ) {
        val item = className?.let { data?.item(it) }
        val building = className?.let { data?.building(it) }
        val schematic = className?.let { data?.schematic(it) }
        when {
            item != null -> {
                val color = if (item.liquid && item.fluidColor.let { it.r + it.g + it.b } > 0) {
                    Color(item.fluidColor.r, item.fluidColor.g, item.fluidColor.b)
                } else {
                    materialColor(item.name, item.className)
                }
                val glyph = if (item.liquid) (if ("gas" in item.name.lowercase()) Glyph.GAS else Glyph.DROP) else glyphFor(item.name, item.className)
                Canvas(Modifier.fillMaxSize().padding(size / 10)) { drawGlyph(glyph, color) }
            }
            building != null -> Icon(buildingIcon(building.name), contentDescription = null, tint = Color(0xFFF08A3E), modifier = Modifier.fillMaxSize().padding(size / 7))
            schematic != null -> Icon(schematicIcon(schematic.type), contentDescription = null, tint = Color(0xFF8FC3E8), modifier = Modifier.fillMaxSize().padding(size / 7))
            else -> Canvas(Modifier.fillMaxSize().padding(size / 10)) { drawGlyph(Glyph.BOX, Color(0xFF6C757D)) }
        }
    }
}

private fun Color.shade(f: Float) = Color(red * f, green * f, blue * f, alpha)
private fun Color.tint(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

private fun poly(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1]); i += 2
    }
    close()
}

/** Draws [glyph] in a 100×100 design space scaled to the canvas. */
fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension / 100f
    val dark = color.shade(0.6f)
    val light = color.tint(0.35f)
    val outline = Stroke(width = 3f, cap = StrokeCap.Round)
    scale(s, s, pivot = Offset.Zero) {
        when (glyph) {
            Glyph.ORE -> {
                val p = poly(20f, 60f, 30f, 28f, 55f, 18f, 80f, 35f, 84f, 66f, 58f, 84f, 30f, 80f)
                drawPath(p, color); drawPath(poly(30f, 28f, 55f, 18f, 80f, 35f, 52f, 44f), light)
                drawPath(poly(52f, 44f, 80f, 35f, 84f, 66f, 58f, 84f), dark); drawPath(p, dark.shade(0.7f), style = outline)
            }
            Glyph.INGOT -> {
                drawPath(poly(14f, 66f, 30f, 40f, 86f, 40f, 70f, 66f), light)
                drawPath(poly(14f, 66f, 70f, 66f, 70f, 80f, 14f, 80f), color)
                drawPath(poly(70f, 66f, 86f, 40f, 86f, 54f, 70f, 80f), dark)
                drawPath(poly(14f, 66f, 30f, 40f, 86f, 40f, 86f, 54f, 70f, 80f, 14f, 80f), dark.shade(0.7f), style = outline)
            }
            Glyph.PLATE, Glyph.SHEET -> {
                val t = if (glyph == Glyph.PLATE) 10f else 5f
                drawPath(poly(10f, 50f, 50f, 28f, 90f, 50f, 50f, 72f), light)
                drawPath(poly(10f, 50f, 50f, 72f, 50f, 72f + t, 10f, 50f + t), color)
                drawPath(poly(50f, 72f, 90f, 50f, 90f, 50f + t, 50f, 72f + t), dark)
                if (glyph == Glyph.PLATE) listOf(Offset(30f, 50f), Offset(50f, 39f), Offset(70f, 50f), Offset(50f, 61f)).forEach { drawCircle(dark, 3f, it) }
            }
            Glyph.ROD -> rotate(-40f, Offset(50f, 50f)) {
                drawRoundRect(color, Offset(44f, 10f), Size(12f, 80f), CornerRadius(6f))
                drawRoundRect(light, Offset(46f, 12f), Size(4f, 76f), CornerRadius(2f))
                drawOval(dark, Offset(44f, 10f), Size(12f, 8f))
            }
            Glyph.WIRE, Glyph.CABLE -> {
                val w = if (glyph == Glyph.CABLE) 9f else 5f
                for (i in 0..3) drawOval(color, Offset(22f + i * 9f, 25f), Size(30f, 50f), style = Stroke(w))
                if (glyph == Glyph.CABLE) drawLine(dark, Offset(78f, 50f), Offset(92f, 80f), 7f, StrokeCap.Round)
            }
            Glyph.SCREW -> rotate(-35f, Offset(50f, 50f)) {
                drawRect(color, Offset(44f, 34f), Size(12f, 54f))
                for (y in 0..5) drawLine(dark, Offset(42f, 40f + y * 8f), Offset(58f, 44f + y * 8f), 3f)
                drawPath(poly(32f, 34f, 68f, 34f, 64f, 16f, 36f, 16f), light)
            }
            Glyph.BEAM -> rotate(-30f, Offset(50f, 50f)) {
                drawRect(color, Offset(18f, 30f), Size(64f, 8f)); drawRect(color, Offset(18f, 62f), Size(64f, 8f))
                drawRect(dark, Offset(46f, 38f), Size(8f, 24f)); drawRect(light, Offset(18f, 30f), Size(64f, 3f))
            }
            Glyph.PIPE -> {
                drawRect(color, Offset(20f, 38f), Size(56f, 24f))
                drawRect(light, Offset(20f, 40f), Size(56f, 5f))
                drawOval(dark, Offset(68f, 38f), Size(16f, 24f)); drawOval(Color(0xFF1A1A1A), Offset(72f, 43f), Size(8f, 14f))
                drawOval(color, Offset(12f, 38f), Size(16f, 24f))
            }
            Glyph.FRAME -> {
                drawRect(color, Offset(16f, 16f), Size(68f, 68f), style = Stroke(10f))
                drawLine(dark, Offset(20f, 20f), Offset(80f, 80f), 7f); drawLine(dark, Offset(80f, 20f), Offset(20f, 80f), 7f)
                listOf(Offset(16f, 16f), Offset(84f, 16f), Offset(16f, 84f), Offset(84f, 84f)).forEach { drawCircle(light, 6f, it) }
            }
            Glyph.GEAR -> {
                val c = Offset(50f, 50f)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(color, c, Offset(50f + 38f * cos(a).toFloat(), 50f + 38f * sin(a).toFloat()), 12f)
                }
                drawCircle(color, 28f, c); drawCircle(dark, 12f, c); drawCircle(light, 28f, c, style = Stroke(3f))
            }
            Glyph.CHIP -> {
                drawRoundRect(Color(0xFF2E7D46), Offset(14f, 18f), Size(72f, 64f), CornerRadius(6f))
                drawRect(Color(0xFF1C1F24), Offset(36f, 36f), Size(28f, 28f))
                for (i in 0..3) {
                    drawLine(color, Offset(20f, 26f + i * 14f), Offset(36f, 26f + i * 14f), 3f)
                    drawLine(color, Offset(64f, 26f + i * 14f), Offset(80f, 26f + i * 14f), 3f)
                }
                drawRect(color, Offset(40f, 40f), Size(20f, 20f), style = Stroke(2f))
            }
            Glyph.CRYSTAL -> {
                drawPath(poly(50f, 10f, 78f, 36f, 64f, 88f, 36f, 88f, 22f, 36f), color)
                drawPath(poly(50f, 10f, 78f, 36f, 50f, 44f), light); drawPath(poly(50f, 44f, 78f, 36f, 64f, 88f), dark)
                drawPath(poly(50f, 10f, 78f, 36f, 64f, 88f, 36f, 88f, 22f, 36f), light, style = Stroke(2f))
            }
            Glyph.POWDER -> {
                drawPath(Path().apply { moveTo(12f, 80f); cubicTo(25f, 40f, 75f, 40f, 88f, 80f); close() }, color)
                drawPath(Path().apply { moveTo(34f, 58f); cubicTo(42f, 46f, 58f, 46f, 64f, 56f) }, light, style = Stroke(4f, cap = StrokeCap.Round))
                for (p in listOf(Offset(30f, 72f), Offset(52f, 66f), Offset(70f, 74f), Offset(44f, 76f))) drawCircle(dark, 2.5f, p)
            }
            Glyph.DROP -> {
                val drop = Path().apply { moveTo(50f, 10f); cubicTo(70f, 40f, 82f, 52f, 82f, 64f); cubicTo(82f, 82f, 68f, 92f, 50f, 92f); cubicTo(32f, 92f, 18f, 82f, 18f, 64f); cubicTo(18f, 52f, 30f, 40f, 50f, 10f); close() }
                drawPath(drop, color); drawPath(drop, dark, style = outline)
                drawOval(light, Offset(30f, 56f), Size(10f, 18f))
            }
            Glyph.GAS -> {
                drawCircle(color.copy(alpha = 0.85f), 22f, Offset(38f, 56f)); drawCircle(color.copy(alpha = 0.85f), 18f, Offset(62f, 46f))
                drawCircle(color.copy(alpha = 0.85f), 14f, Offset(64f, 70f)); drawCircle(light, 6f, Offset(32f, 50f))
            }
            Glyph.CANISTER -> {
                drawRoundRect(color, Offset(26f, 22f), Size(48f, 66f), CornerRadius(10f))
                drawRect(dark, Offset(40f, 10f), Size(20f, 14f)); drawRect(light, Offset(32f, 30f), Size(6f, 50f))
                drawRoundRect(Color(0xFFE6E6E6), Offset(34f, 44f), Size(32f, 18f), CornerRadius(3f))
            }
            Glyph.FUEL_ROD, Glyph.CELL -> {
                val h = if (glyph == Glyph.FUEL_ROD) 80f else 56f
                val top = 50f - h / 2
                drawRoundRect(Color(0xFF8A939E), Offset(32f, top), Size(36f, h), CornerRadius(8f))
                drawRoundRect(color, Offset(38f, top + 8f), Size(24f, h - 16f), CornerRadius(4f))
                drawRect(light.copy(alpha = 0.7f), Offset(42f, top + 10f), Size(5f, h - 20f))
            }
            Glyph.BATTERY -> {
                drawRoundRect(Color(0xFF2B2F35), Offset(24f, 20f), Size(52f, 70f), CornerRadius(8f))
                drawRect(Color(0xFF8A939E), Offset(40f, 10f), Size(20f, 10f))
                drawRoundRect(color, Offset(30f, 44f), Size(40f, 40f), CornerRadius(4f))
                drawPath(poly(54f, 26f, 42f, 50f, 52f, 50f, 46f, 70f, 60f, 44f, 50f, 44f), Color(0xFFFFE066))
            }
            Glyph.LEAF -> {
                val leaf = Path().apply { moveTo(20f, 80f); cubicTo(20f, 30f, 60f, 15f, 85f, 15f); cubicTo(85f, 45f, 70f, 85f, 20f, 80f); close() }
                drawPath(leaf, color); drawLine(dark, Offset(22f, 78f), Offset(70f, 30f), 3f)
            }
            Glyph.REMAINS -> {
                drawLine(Color(0xFFE8E2D0), Offset(25f, 75f), Offset(75f, 25f), 12f, StrokeCap.Round)
                listOf(Offset(22f, 70f), Offset(30f, 80f), Offset(70f, 20f), Offset(80f, 30f)).forEach { drawCircle(Color(0xFFE8E2D0), 9f, it) }
                drawCircle(color, 8f, Offset(50f, 50f))
            }
            Glyph.AMMO -> rotate(-35f, Offset(50f, 50f)) {
                drawRect(Color(0xFFC9A34A), Offset(40f, 44f), Size(20f, 40f))
                drawPath(Path().apply { moveTo(40f, 44f); cubicTo(40f, 20f, 60f, 20f, 60f, 44f); close() }, color)
                drawRect(Color(0xFF8A6F2E), Offset(40f, 78f), Size(20f, 6f))
            }
            Glyph.BOMB -> {
                drawCircle(color, 30f, Offset(46f, 56f)); drawCircle(light, 8f, Offset(36f, 46f))
                drawLine(Color(0xFF8A939E), Offset(62f, 34f), Offset(76f, 18f), 6f, StrokeCap.Round)
                drawCircle(Color(0xFFFFB13B), 5f, Offset(80f, 14f))
            }
            Glyph.TOOL -> rotate(-45f, Offset(50f, 50f)) {
                drawRoundRect(Color(0xFF8A939E), Offset(44f, 34f), Size(12f, 56f), CornerRadius(5f))
                drawPath(poly(30f, 12f, 70f, 12f, 70f, 30f, 58f, 38f, 42f, 38f, 30f, 30f), color)
                drawRect(Color(0xFF2B2F35), Offset(44f, 12f), Size(12f, 12f))
            }
            Glyph.ELEVATOR_PART -> {
                val hex = Path().apply {
                    for (i in 0 until 6) {
                        val a = PI / 3 * i - PI / 2
                        val x = 50f + 38f * cos(a).toFloat(); val y = 50f + 38f * sin(a).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(hex, Color(0xFFDF691A)); drawPath(hex, Color(0xFFFFD0A8), style = Stroke(3f))
                drawCircle(Color(0xFF2B2F35), 14f, Offset(50f, 50f)); drawCircle(light, 6f, Offset(50f, 50f))
            }
            Glyph.CUBE -> {
                drawPath(poly(50f, 14f, 84f, 32f, 50f, 50f, 16f, 32f), light)
                drawPath(poly(16f, 32f, 50f, 50f, 50f, 88f, 16f, 70f), color)
                drawPath(poly(50f, 50f, 84f, 32f, 84f, 70f, 50f, 88f), dark)
            }
            Glyph.COUPON -> {
                drawRoundRect(color, Offset(12f, 28f), Size(76f, 44f), CornerRadius(6f))
                drawCircle(Color(0xFF23272C), 7f, Offset(12f, 50f)); drawCircle(Color(0xFF23272C), 7f, Offset(88f, 50f))
                drawRect(light, Offset(28f, 42f), Size(44f, 4f)); drawRect(light, Offset(28f, 52f), Size(30f, 4f))
            }
            Glyph.SPHERE -> {
                drawCircle(color, 34f, Offset(50f, 50f))
                drawCircle(dark, 34f, Offset(50f, 50f), style = Stroke(3f))
                drawOval(light, Offset(30f, 28f), Size(18f, 12f))
                drawOval(dark, Offset(16f, 44f), Size(68f, 12f), style = Stroke(2f))
            }
            Glyph.FILTER -> {
                drawCircle(Color(0xFF8A939E), 32f, Offset(50f, 50f)); drawCircle(color, 22f, Offset(50f, 50f))
                for (i in 0 until 6) {
                    val a = i * PI / 3
                    drawLine(dark, Offset(50f, 50f), Offset(50f + 22f * cos(a).toFloat(), 50f + 22f * sin(a).toFloat()), 2.5f)
                }
            }
            Glyph.FABRIC -> {
                drawPath(Path().apply { moveTo(16f, 26f); cubicTo(40f, 16f, 60f, 36f, 84f, 26f); lineTo(84f, 74f); cubicTo(60f, 84f, 40f, 64f, 16f, 74f); close() }, color)
                for (i in 1..3) drawLine(dark, Offset(16f + i * 17f, 28f), Offset(16f + i * 17f, 76f), 1.5f)
            }
            Glyph.BOX -> {
                drawPath(poly(50f, 16f, 84f, 32f, 50f, 48f, 16f, 32f), light)
                drawPath(poly(16f, 32f, 50f, 48f, 50f, 86f, 16f, 68f), color)
                drawPath(poly(50f, 48f, 84f, 32f, 84f, 68f, 50f, 86f), dark)
            }
        }
    }
}
