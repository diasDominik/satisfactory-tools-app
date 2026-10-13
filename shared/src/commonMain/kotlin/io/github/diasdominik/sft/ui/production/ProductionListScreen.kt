package io.github.diasdominik.sft.ui.production

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.SftFormat
import io.github.diasdominik.sft.platform.rememberTextSharer
import io.github.diasdominik.sft.state.AppState
import io.github.diasdominik.sft.state.ProductionTab
import io.github.diasdominik.sft.state.ResultStatus
import io.github.diasdominik.sft.ui.EmptyState
import io.github.diasdominik.sft.ui.EntityIcon
import io.github.diasdominik.sft.ui.fmt
import kotlinx.coroutines.launch

fun exportTabs(tabs: List<ProductionTab>): String = SftFormat.export(tabs.map { it.data }, tabs.map { it.name })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductionListScreen(
    state: AppState,
    data: GameData,
    onOpen: (Long) -> Unit,
    actions: @Composable () -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>() }
    var showImport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val share = rememberTextSharer()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selecting = selected.isNotEmpty()

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, contentDescription = "Clear selection") } },
                    actions = {
                        IconButton(onClick = { selected.clear(); selected.addAll(state.tabs.map { it.id }) }) { Icon(Icons.Default.SelectAll, contentDescription = "Select all") }
                        IconButton(onClick = {
                            val tabs = state.tabs.filter { it.id in selected }
                            share("Satisfactory Tools export", exportTabs(tabs))
                        }) { Icon(Icons.Default.Share, contentDescription = "Export selected") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Remove selected") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Production lines") },
                    actions = {
                        IconButton(onClick = { showImport = true }) { Icon(Icons.Default.FileDownload, contentDescription = "Import") }
                        if (state.tabs.isNotEmpty()) {
                            IconButton(onClick = { share("Satisfactory Tools export", exportTabs(state.tabs.toList())) }) {
                                Icon(Icons.Default.Share, contentDescription = "Export all")
                            }
                        }
                        actions()
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { onOpen(state.addTab().id) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New production line") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.tabs.isEmpty()) {
                item {
                    EmptyState(
                        "No production lines yet.\n\nCreate one to plan a factory: pick what to produce, and the planner works out " +
                            "the recipes, machines, power and raw resources needed. Long-press a line to select several.",
                    )
                }
            }
            items(state.tabs, key = { it.id }) { tab ->
                val isSelected = tab.id in selected
                Card(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (selecting) toggle(selected, tab.id) else onOpen(tab.id) },
                        onLongClick = { toggle(selected, tab.id) },
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            Checkbox(isSelected, onCheckedChange = { toggle(selected, tab.id) })
                        }
                        EntityIcon(data, tab.icon, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tab.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                summary(data, tab),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!selecting) TabMenu(state, tab, share)
                    }
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val tabs = SftFormat.import(text)
                tabs.forEach { state.addTab(it) }
                showImport = false
                scope.launch { snackbar.showSnackbar("Imported ${tabs.size} production line${if (tabs.size == 1) "" else "s"}.") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${selected.size} production line${if (selected.size == 1) "" else "s"}?") },
            text = { Text("This can't be undone. Export them first if you want to keep a copy.") },
            confirmButton = {
                TextButton(onClick = {
                    state.removeTabs(state.tabs.filter { it.id in selected })
                    selected.clear()
                    confirmDelete = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun toggle(list: MutableList<Long>, id: Long) {
    if (!list.remove(id)) list.add(id)
}

private fun summary(data: GameData, tab: ProductionTab): String {
    val products = tab.request.production.filter { it.item != null }.joinToString(", ") {
        val name = data.nameOf(it.item!!)
        if (it.type == ProductionType.MAXIMIZE) "$name (max)" else "${fmt(it.amount)}/min $name"
    }.ifEmpty { "No products selected" }
    val result = tab.result
    val status = when (tab.status) {
        ResultStatus.RESULT -> if (result != null) " · ${result.buildings.amount} buildings · ${fmt(result.power.total.average)} MW" else ""
        ResultStatus.NO_RESULT -> " · no solution"
        ResultStatus.CALCULATING -> " · calculating…"
        ResultStatus.NO_INPUT -> ""
    }
    return products + status
}

@Composable
private fun TabMenu(state: AppState, tab: ProductionTab, share: (String, String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
    DropdownMenu(open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Clone") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { open = false; state.cloneTab(tab) })
        DropdownMenuItem(text = { Text("Export") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { open = false; share(tab.name, exportTabs(listOf(tab))) })
        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; state.removeTab(tab) })
    }
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import production lines") },
        text = {
            Column {
                Text(
                    "Paste the contents of a .sft export (from this app or satisfactorytools.com).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp).padding(top = 8.dp),
                    placeholder = { Text("# Satisfactory Tools export…") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it; error = null } }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                try {
                    onImport(text)
                } catch (e: Exception) {
                    error = "Couldn't import: " + (e.message ?: "invalid data")
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
