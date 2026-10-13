package io.github.diasdominik.sft.ui.production

import io.github.diasdominik.sft.ui.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.platform.rememberTextSharer
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.state.ProductionTab
import io.github.diasdominik.sft.state.ResultStatus
import io.github.diasdominik.sft.ui.EntityPickerDialog
import io.github.diasdominik.sft.ui.codex.CodexLinks

enum class SetupSection(val label: String) {
    PRODUCTS("Products"),
    RESOURCES("Raw resources"),
    INPUTS("Inputs"),
    ALTERNATES("Alternate recipes"),
    BASIC("Basic recipes"),
    MACHINES("Machines"),
}

enum class ResultSection(val label: String) {
    OVERVIEW("Overview"),
    GRAPH("Visualization"),
    ITEMS("Items"),
    BUILDINGS("Buildings"),
    POWER("Power"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionTabScreen(state: AppState, data: GameData, tab: ProductionTab, links: CodexLinks, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    var setupSection by rememberSaveable { mutableStateOf(SetupSection.PRODUCTS.name) }
    var resultSection by rememberSaveable { mutableStateOf(ResultSection.OVERVIEW.name) }
    var renaming by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val share = rememberTextSharer()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tab.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back") } },
            actions = {
                IconButton(onClick = { share(tab.name, exportTabs(listOf(tab))) }) { Icon(AppIcons.Share, contentDescription = "Export") }
                IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, contentDescription = "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Change icon") }, leadingIcon = { Icon(AppIcons.Image, null) }, onClick = { menu = false; pickingIcon = true })
                    DropdownMenuItem(text = { Text("Clone") }, leadingIcon = { Icon(AppIcons.Copy, null) }, onClick = { menu = false; state.cloneTab(tab) })
                    DropdownMenuItem(text = { Text("Reset to default") }, leadingIcon = { Icon(AppIcons.Reset, null) }, onClick = { menu = false; confirmReset = true })
                    DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                }
            },
        )
    }) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = page) {
                Tab(page == 0, onClick = { page = 0 }, text = { Text("Setup") })
                Tab(page == 1, onClick = { page = 1 }, text = {
                    Text(
                        when (tab.status) {
                            ResultStatus.CALCULATING -> "Result (calculating…)"
                            ResultStatus.NO_RESULT -> "Result (no solution)"
                            else -> "Result"
                        },
                    )
                })
            }
            val sections: List<Pair<String, String>> =
                if (page == 0) SetupSection.entries.map { it.name to it.label } else ResultSection.entries.map { it.name to it.label }
            val current = if (page == 0) setupSection else resultSection
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sections, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = current == key,
                        onClick = { if (page == 0) setupSection = key else resultSection = key },
                        label = { Text(label) },
                    )
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (page == 0) {
                    setupContent(SetupSection.valueOf(setupSection), data, tab)
                } else {
                    resultContent(ResultSection.valueOf(resultSection), data, tab, links)
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(tab.data.metadata.name ?: tab.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename production line") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Leave empty for automatic name") }) },
            confirmButton = { TextButton(onClick = { tab.rename(name); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (pickingIcon) {
        val options = remember(tab) {
            (tab.request.production.mapNotNull { it.item } + data.items.values.sortedBy { it.name }.map { it.className } +
                data.buildings.values.sortedBy { it.name }.map { it.className }).distinct()
        }
        EntityPickerDialog(data, "Choose icon", options, onDismiss = { pickingIcon = false }) {
            tab.setIcon(it)
            pickingIcon = false
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset this production line?") },
            text = { Text("Products, inputs, limits and recipe choices go back to their defaults.") },
            confirmButton = { TextButton(onClick = { tab.data = state.newProductionData().copy(metadata = tab.data.metadata); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove \"${tab.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); state.removeTab(tab) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

internal fun LazyListScope.setupContent(section: SetupSection, data: GameData, tab: ProductionTab) {
    when (section) {
        SetupSection.PRODUCTS -> productsSection(data, tab)
        SetupSection.RESOURCES -> resourcesSection(data, tab)
        SetupSection.INPUTS -> inputsSection(data, tab)
        SetupSection.ALTERNATES -> recipeToggleSection(data, tab, alternates = true)
        SetupSection.BASIC -> recipeToggleSection(data, tab, alternates = false)
        SetupSection.MACHINES -> machinesSection(data, tab)
    }
}

internal fun LazyListScope.resultContent(section: ResultSection, data: GameData, tab: ProductionTab, links: CodexLinks) {
    val result = tab.result
    if (result == null || tab.status != ResultStatus.RESULT) {
        item { StatusMessage(tab.status) }
        return
    }
    when (section) {
        ResultSection.OVERVIEW -> overviewSection(data, tab, result, links)
        ResultSection.GRAPH -> graphSection(data, result, links)
        ResultSection.ITEMS -> itemsSection(data, result, links)
        ResultSection.BUILDINGS -> buildingsSection(data, result, links)
        ResultSection.POWER -> powerSection(data, result, links)
    }
}
