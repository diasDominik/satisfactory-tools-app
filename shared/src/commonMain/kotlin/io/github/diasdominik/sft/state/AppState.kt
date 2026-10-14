package io.github.diasdominik.sft.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.diasdominik.sft.data.Constants
import io.github.diasdominik.sft.data.DataLoader
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.GameVersion
import io.github.diasdominik.sft.icons.IconStore
import io.github.diasdominik.sft.planner.BoostPlan
import io.github.diasdominik.sft.planner.BuildDiff
import io.github.diasdominik.sft.planner.GraphPoint
import io.github.diasdominik.sft.planner.InputItem
import io.github.diasdominik.sft.planner.ProductionData
import io.github.diasdominik.sft.planner.ProductionDiagnosis
import io.github.diasdominik.sft.planner.ProductionGraph
import io.github.diasdominik.sft.planner.ProductionItem
import io.github.diasdominik.sft.planner.ProductionRequest
import io.github.diasdominik.sft.planner.ProductionResult
import io.github.diasdominik.sft.planner.ProductionSolver
import io.github.diasdominik.sft.planner.ProductionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.random.Random
import org.koin.core.annotation.KoinViewModel

enum class ResultStatus { NO_INPUT, CALCULATING, RESULT, NO_RESULT }

class ProductionTab(val id: Long, initial: ProductionData, private val gameData: GameData) {
    var data by mutableStateOf(initial)
    var status by mutableStateOf(ResultStatus.NO_INPUT)
        internal set
    var result by mutableStateOf<ProductionResult?>(null)
        internal set
    /** Why there is no result, when status is NO_RESULT. */
    var problems by mutableStateOf<List<String>>(emptyList())
        internal set

    val request: ProductionRequest get() = data.request

    fun updateRequest(transform: (ProductionRequest) -> ProductionRequest) {
        data = data.copy(request = transform(data.request))
    }

    fun rename(name: String?) {
        data = data.copy(metadata = data.metadata.copy(name = name?.takeIf { it.isNotBlank() }))
    }

    fun toggleBuilt(key: String) {
        val built = data.metadata.built
        data = data.copy(metadata = data.metadata.copy(built = if (key in built) built - key else built + key))
    }

    fun moveGraphNode(key: String, x: Float, y: Float) {
        data = data.copy(metadata = data.metadata.copy(graphPositions = data.metadata.graphPositions + (key to GraphPoint(x, y))))
    }

    fun resetGraphLayout() {
        data = data.copy(metadata = data.metadata.copy(graphPositions = emptyMap()))
    }

    fun setBeltTier(tier: Int) {
        data = data.copy(metadata = data.metadata.copy(beltTier = tier))
    }

    fun setPipeTier(tier: Int) {
        data = data.copy(metadata = data.metadata.copy(pipeTier = tier))
    }

    fun toggleAmplifiedRecipe(recipe: String) {
        updateRequest { r ->
            r.copy(amplifiedRecipes = if (recipe in r.amplifiedRecipes) r.amplifiedRecipes - recipe else r.amplifiedRecipes + recipe)
        }
    }

    fun toggleAmplifiedMachine(machine: String) {
        updateRequest { r ->
            r.copy(amplifiedMachines = if (machine in r.amplifiedMachines) r.amplifiedMachines - machine else r.amplifiedMachines + machine)
        }
    }

    val uid: String get() = data.metadata.uid.orEmpty()

    /** What this line hands on to others: its products and byproducts per minute. */
    val outputs: Map<String, Double>
        get() {
            val result = result ?: return emptyMap()
            val out = LinkedHashMap(result.output)
            result.byproducts.forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v }
            return out
        }

    fun setOwned(somersloops: Int = data.metadata.somersloopsOwned, shards: Int = data.metadata.powerShardsOwned) {
        data = data.copy(metadata = data.metadata.copy(somersloopsOwned = somersloops, powerShardsOwned = shards))
    }

    /** Remembers the current plan as what's built, so later changes show as an upgrade. */
    fun markPlanBuilt(result: ProductionResult) {
        data = data.copy(metadata = data.metadata.copy(builtPlan = BuildDiff.snapshot(result)))
    }

    fun applyBoost(plan: BoostPlan) {
        updateRequest { r -> r.copy(amplifiedRecipes = plan.amplifiedRecipes, amplifiedMachines = emptyList(), recipeClockSpeed = plan.recipeClockSpeed) }
    }

    fun setIcon(icon: String?) {
        data = data.copy(metadata = data.metadata.copy(icon = icon))
    }

    val name: String
        get() = data.metadata.name
            ?: request.production.firstOrNull { it.item != null }?.let { gameData.item(it.item)?.name?.plus(" Factory") }
            ?: "Unnamed Factory"

    val icon: String?
        get() = data.metadata.icon ?: request.production.firstOrNull { it.item != null }?.item

    internal var job: Job? = null
}

@KoinViewModel
class AppState(private val settings: SettingsStore, val icons: IconStore) : ViewModel() {
    private val scope: CoroutineScope get() = viewModelScope
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var version by mutableStateOf(GameVersion.fromId(settings.getString(KEY_VERSION)))
        private set
    var gameData by mutableStateOf<GameData?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    val tabs = mutableStateListOf<ProductionTab>()

    /** The alternate recipes the player has unlocked (from hard drives); new lines start with them. */
    var unlockedAlternates by mutableStateOf<List<String>?>(null)
        private set
    private var nextId = 1L

    init {
        load(version)
    }

    fun switchVersion(newVersion: GameVersion) {
        if (newVersion == version && gameData != null) return
        save()
        version = newVersion
        settings.putString(KEY_VERSION, newVersion.id)
        load(newVersion)
    }

    private fun load(target: GameVersion) {
        gameData = null
        loadError = null
        tabs.forEach { it.job?.cancel() }
        tabs.clear()
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) { DataLoader.load(target) }
                if (target != version) return@launch
                gameData = loaded
                restoreTabs(loaded)
                // Fetched in the background once per game version; the app's own icons show until then.
                launch { icons.sync(loaded) }
            } catch (e: Throwable) {
                loadError = e.message ?: e.toString()
            }
        }
    }

    /** Adds [recipe] to the unlocked alternates and enables it in every production line. */
    fun unlockAlternate(recipe: String) {
        saveUnlockedAlternates((unlockedAlternates.orEmpty() + recipe).distinct())
        tabs.forEach { tab ->
            if (recipe !in tab.request.allowedAlternateRecipes) tab.updateRequest { r -> r.copy(allowedAlternateRecipes = r.allowedAlternateRecipes + recipe) }
        }
    }

    /** Fetches every game icon again, for example after the wiki updated them. */
    fun redownloadIcons() {
        val data = gameData ?: return
        scope.launch { icons.sync(data, force = true) }
    }

    fun saveUnlockedAlternates(recipes: List<String>) {
        val data = gameData ?: return
        unlockedAlternates = recipes
        settings.putString(unlockedKey(data.version), json.encodeToString(ListSerializer(String.serializer()), recipes))
    }

    private fun restoreTabs(data: GameData) {
        unlockedAlternates = settings.getString(unlockedKey(data.version))?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        }
        val stored = settings.getString(tabsKey(data.version))
        val list = stored?.let {
            runCatching { json.decodeFromString(ListSerializer(ProductionData.serializer()), it) }.getOrNull()
        } ?: emptyList()
        list.forEach { addTab(it, persist = false) }
    }

    fun newProductionData(): ProductionData = ProductionData(
        request = ProductionRequest(
            resourceMax = Constants.RESOURCE_AMOUNTS,
            resourceWeight = Constants.RESOURCE_WEIGHTS,
            production = listOf(ProductionItem(type = ProductionType.PER_MINUTE, amount = 10.0, ratio = 100.0)),
            allowedAlternateRecipes = unlockedAlternates.orEmpty(),
        ),
    )

    fun addTab(production: ProductionData = newProductionData(), persist: Boolean = true): ProductionTab {
        val data = gameData ?: error("Game data not loaded")
        val tab = ProductionTab(nextId++, normalise(production), data)
        tabs += tab
        watch(tab, data)
        if (persist) save()
        return tab
    }

    /** A new line making [amount] per minute of [fuel], from the power plant planner. */
    fun addFuelLine(fuel: String, amount: Double, name: String): ProductionTab {
        val base = newProductionData()
        return addTab(
            base.copy(
                metadata = base.metadata.copy(name = name),
                request = base.request.copy(production = listOf(ProductionItem(item = fuel, amount = amount))),
            ),
        )
    }

    fun cloneTab(tab: ProductionTab): ProductionTab {
        val copy = tab.data.copy(
            metadata = tab.data.metadata.copy(name = tab.data.metadata.name?.let { "$it (clone)" } ?: (tab.name + " (clone)"), uid = null),
        )
        return addTab(copy)
    }

    fun removeTab(tab: ProductionTab) = removeTabs(listOf(tab))

    fun removeTabs(selected: Collection<ProductionTab>) {
        selected.forEach { it.job?.cancel() }
        tabs.removeAll(selected.toSet())
        // Inputs taken from a removed line keep their last amount but no longer follow it.
        val gone = selected.map { it.uid }.toSet()
        for (other in tabs) {
            if (other.request.input.any { it.source in gone }) {
                other.updateRequest { r -> r.copy(input = r.input.map { if (it.source in gone) it.copy(source = null) else it }) }
            }
        }
        save()
    }

    fun tabByUid(uid: String?): ProductionTab? = uid?.let { u -> tabs.firstOrNull { it.uid == u } }

    /** Lines [target] can take inputs from: any other line that doesn't already depend on it. */
    fun linkableSources(target: ProductionTab): List<ProductionTab> = tabs.filter { it !== target && !dependsOn(it, target) }

    /** Whether [tab] takes inputs, directly or through other lines, from [other]. */
    private fun dependsOn(tab: ProductionTab, other: ProductionTab): Boolean {
        val seen = HashSet<String>()
        val queue = ArrayDeque(listOf(tab))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current.uid)) continue
            for (input in current.request.input) {
                val source = tabByUid(input.source) ?: continue
                if (source === other) return true
                queue += source
            }
        }
        return false
    }

    /** Adds every product and byproduct of [source] as an input of [target], kept in step with [source]. */
    fun linkLine(target: ProductionTab, source: ProductionTab) {
        if (source === target || dependsOn(source, target)) return
        val outputs = source.outputs.ifEmpty { source.request.production.filter { it.item != null }.associate { it.item!! to it.amount } }
        target.updateRequest { r ->
            val kept = r.input.filter { it.source != source.uid }
            r.copy(input = kept + outputs.map { (item, amount) -> InputItem(item, amount, source.uid) })
        }
    }

    /** Brings inputs taken from [source] up to date with its latest result. */
    private fun propagate(source: ProductionTab) {
        val outputs = source.outputs
        for (target in tabs) {
            if (target === source || target.request.input.none { it.source == source.uid }) continue
            val updated = target.request.input.map { input ->
                if (input.source != source.uid || input.item == null) input else input.copy(amount = outputs[input.item] ?: 0.0)
            }
            val changed = updated.zip(target.request.input).any { (a, b) -> abs(a.amount - b.amount) > 1e-6 }
            if (changed) target.updateRequest { r -> r.copy(input = updated) }
        }
    }

    fun moveTab(from: Int, to: Int) {
        if (from !in tabs.indices || to !in tabs.indices) return
        val tab = tabs.removeAt(from)
        tabs.add(to, tab)
        save()
    }

    fun tab(id: Long): ProductionTab? = tabs.firstOrNull { it.id == id }

    fun save() {
        val data = gameData ?: return
        val payload = json.encodeToString(ListSerializer(ProductionData.serializer()), tabs.map { it.data })
        settings.putString(tabsKey(data.version), payload)
    }

    /** Fills in fields older exports may lack, mirroring upstream's import fix-ups. */
    private fun normalise(production: ProductionData): ProductionData {
        val request = production.request
        val resourceMax = LinkedHashMap(Constants.RESOURCE_AMOUNTS)
        request.resourceMax.forEach { (k, v) -> resourceMax[k] = v }
        val uid = production.metadata.uid?.takeIf { u -> tabs.none { it.uid == u } } ?: newUid()
        return production.copy(
            metadata = production.metadata.copy(uid = uid),
            request = request.copy(resourceMax = resourceMax, resourceWeight = Constants.RESOURCE_WEIGHTS),
        )
    }

    private fun newUid(): String = Random.nextBytes(12).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @OptIn(FlowPreview::class)
    private fun watch(tab: ProductionTab, data: GameData) {
        val solver = ProductionSolver(data)
        tab.job = scope.launch {
            // Save every change right away; renaming or a new icon doesn't need a new plan.
            launch { snapshotFlow { tab.data }.drop(1).collect { save() } }
            var first = true
            snapshotFlow { tab.data.request }
                .debounce { if (first) 0L else 350L }
                .collectLatest { request ->
                    first = false
                    if (request.production.none { it.item != null && (it.amount > 0 || it.type == ProductionType.MAXIMIZE) }) {
                        tab.status = ResultStatus.NO_INPUT
                        tab.result = null
                        propagate(tab)
                        return@collectLatest
                    }
                    tab.status = ResultStatus.CALCULATING
                    val (result, problems) = withContext(Dispatchers.Default) {
                        val result = solver.solve(request)?.let { ProductionResult(request, ProductionGraph.from(it), data) }
                        result to if (result == null) ProductionDiagnosis(data).explain(request) else emptyList()
                    }
                    tab.result = result
                    tab.problems = problems
                    tab.status = if (result == null) ResultStatus.NO_RESULT else ResultStatus.RESULT
                    propagate(tab)
                }
        }
    }

    private companion object {
        const val KEY_VERSION = "version"
        fun tabsKey(version: GameVersion) = "tabs-" + version.id
        fun unlockedKey(version: GameVersion) = "unlocked-alternates-" + version.id
    }
}
