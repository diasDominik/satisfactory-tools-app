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
import kotlinx.serialization.json.Json
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
class AppState(private val settings: SettingsStore) : ViewModel() {
    private val scope: CoroutineScope get() = viewModelScope
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var version by mutableStateOf(GameVersion.fromId(settings.getString(KEY_VERSION)))
        private set
    var gameData by mutableStateOf<GameData?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    val tabs = mutableStateListOf<ProductionTab>()
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
            } catch (e: Throwable) {
                loadError = e.message ?: e.toString()
            }
        }
    }

    private fun restoreTabs(data: GameData) {
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

    fun cloneTab(tab: ProductionTab): ProductionTab {
        val copy = tab.data.copy(metadata = tab.data.metadata.copy(name = tab.data.metadata.name?.let { "$it (clone)" } ?: (tab.name + " (clone)")))
        return addTab(copy)
    }

    fun removeTab(tab: ProductionTab) {
        tab.job?.cancel()
        tabs.remove(tab)
        save()
    }

    fun removeTabs(selected: Collection<ProductionTab>) {
        selected.forEach { it.job?.cancel() }
        tabs.removeAll(selected.toSet())
        save()
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
        return production.copy(request = request.copy(resourceMax = resourceMax, resourceWeight = Constants.RESOURCE_WEIGHTS))
    }

    @OptIn(FlowPreview::class)
    private fun watch(tab: ProductionTab, data: GameData) {
        val solver = ProductionSolver(data)
        tab.job = scope.launch {
            var first = true
            snapshotFlow { tab.data }
                .debounce { if (first) 0L else 350L }
                .collectLatest { production ->
                    if (!first) save()
                    first = false
                    val request = production.request
                    if (request.production.none { it.item != null && (it.amount > 0 || it.type == ProductionType.MAXIMIZE) }) {
                        tab.status = ResultStatus.NO_INPUT
                        tab.result = null
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
                }
        }
    }

    private companion object {
        const val KEY_VERSION = "version"
        fun tabsKey(version: GameVersion) = "tabs-" + version.id
    }
}
