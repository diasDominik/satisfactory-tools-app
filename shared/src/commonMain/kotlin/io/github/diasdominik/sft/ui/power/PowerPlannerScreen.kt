package io.github.diasdominik.sft.ui.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.PowerPlanner
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.EntityRow
import io.github.diasdominik.sft.ui.KeyValueRow
import io.github.diasdominik.sft.ui.NumberField
import io.github.diasdominik.sft.ui.SectionCard
import io.github.diasdominik.sft.ui.fmt
import kotlin.math.max

/**
 * Sizes a power plant: pick a generator and its fuel, then either a number of generators or the
 * power you need. Shows the output, fuel, water and waste, and can plan the fuel as a production line.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PowerPlannerScreen(
    data: GameData,
    /** Starts a production line making [amount] per minute of [fuel]. */
    onPlanFuel: (fuel: String, amount: Double, name: String) -> Unit,
    actions: @Composable () -> Unit,
) {
    val generators = remember(data) {
        data.generators.values.filter { g -> g.fuel.any { data.item(it) != null } && data.building(g.className) != null }
            .sortedBy { it.powerProduction }
    }
    if (generators.isEmpty()) return
    var generatorClass by rememberSaveable { mutableStateOf(generators.first().className) }
    val generator = generators.firstOrNull { it.className == generatorClass } ?: generators.first()
    val fuels = generator.fuel.filter { (data.item(it)?.energyValue ?: 0.0) > 0 }
    var fuelClass by rememberSaveable(generator.className) { mutableStateOf(fuels.firstOrNull().orEmpty()) }
    var byTarget by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableDoubleStateOf(1.0) }
    var target by rememberSaveable { mutableDoubleStateOf(1000.0) }
    var clock by rememberSaveable { mutableDoubleStateOf(100.0) }

    val generatorCount = if (byTarget) PowerPlanner.generatorsFor(data, generator.className, target, clock) else max(0, count.toInt())
    val plan = PowerPlanner.plan(data, generator.className, fuelClass, generatorCount, clock)

    Scaffold(topBar = { TopAppBar(title = { Text("Power") }, actions = { actions() }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard("Generator") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        generators.forEach { g ->
                            FilterChip(
                                selected = g.className == generator.className,
                                onClick = { generatorClass = g.className },
                                label = { Text(data.nameOf(g.className)) },
                                leadingIcon = { EntityIcon(data, g.className, 20.dp) },
                            )
                        }
                    }
                    Text("Fuel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fuels.forEach { fuel ->
                            FilterChip(
                                selected = fuel == fuelClass,
                                onClick = { fuelClass = fuel },
                                label = { Text(data.nameOf(fuel)) },
                                leadingIcon = { EntityIcon(data, fuel, 20.dp) },
                            )
                        }
                    }
                }
            }
            item {
                SectionCard("Size") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!byTarget, { byTarget = false }, label = { Text("Number of generators") })
                        FilterChip(byTarget, { byTarget = true }, label = { Text("Power I need") })
                    }
                    if (byTarget) {
                        NumberField(target, { if (it >= 0) target = it }, Modifier.fillMaxWidth(), label = "Power", suffix = "MW")
                    } else {
                        NumberField(count, { if (it >= 0) count = it }, Modifier.fillMaxWidth(), label = "Generators")
                    }
                    NumberField(
                        clock,
                        { if (it > 0) clock = it.coerceIn(ProductionRequest.MIN_CLOCK_SPEED, ProductionRequest.MAX_CLOCK_SPEED) },
                        Modifier.fillMaxWidth(),
                        label = "Clock speed (1–250 %)",
                        suffix = "%",
                    )
                }
            }
            if (plan != null) {
                item {
                    SectionCard("Power plant") {
                        KeyValueRow("Generators", "${plan.generators}× ${data.nameOf(plan.generator)}")
                        KeyValueRow("Each", "${fmt(plan.powerPerGenerator)} MW")
                        KeyValueRow("Total power", "${fmt(plan.totalPower)} MW")
                        val unit = if (data.item(plan.fuel)?.liquid == true) "m³/min" else "/min"
                        KeyValueRow(data.nameOf(plan.fuel), "${fmt(plan.fuelPerMinute)} $unit")
                        if (plan.waterPerMinute > 0) {
                            KeyValueRow("Water", "${fmt(plan.waterPerMinute)} m³/min")
                            KeyValueRow("Water Extractors at 100 %", "${plan.waterExtractors}")
                        }
                        plan.waste?.let { (waste, amount) -> KeyValueRow(data.nameOf(waste), "${fmt(amount)} /min") }
                    }
                }
                if (plan.fuelPerMinute > 0) {
                    item {
                        EntityRow(
                            data, plan.fuel,
                            subtitle = "Make a production line for the fuel these generators burn.",
                            trailing = {
                                Button(onClick = {
                                    onPlanFuel(plan.fuel, plan.fuelPerMinute, "${data.nameOf(plan.fuel)} for ${fmt(plan.totalPower)} MW")
                                }) { Text("Plan fuel") }
                            },
                        )
                    }
                }
            }
        }
    }
}
