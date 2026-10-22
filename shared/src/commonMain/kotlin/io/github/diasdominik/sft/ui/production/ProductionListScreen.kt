package io.github.diasdominik.sft.ui.production

import io.github.diasdominik.sft.ui.AppIcons
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
import io.github.diasdominik.sft.planner.Folder
import io.github.diasdominik.sft.planner.ProductionType
import io.github.diasdominik.sft.planner.SftFormat
import io.github.diasdominik.sft.platform.rememberSftFileReader
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
    onHardDrive: () -> Unit = {},
    onFolder: (String?) -> Unit = {},
    actions: @Composable () -> Unit,
) {
    var naming by remember { mutableStateOf<FolderNaming?>(null) }
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
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(AppIcons.Close, contentDescription = "Clear selection") } },
                    actions = {
                        IconButton(onClick = { selected.clear(); selected.addAll(state.tabs.map { it.id }) }) { Icon(AppIcons.SelectAll, contentDescription = "Select all") }
                        IconButton(onClick = {
                            val tabs = state.tabs.filter { it.id in selected }
                            share("Satisfactory Tools export", exportTabs(tabs))
                        }) { Icon(AppIcons.Share, contentDescription = "Export selected") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Delete, contentDescription = "Remove selected") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Production lines") },
                    actions = {
                        IconButton(onClick = onHardDrive) { Icon(AppIcons.HardDrive, contentDescription = "Hard drive choice") }
                        IconButton(onClick = { showImport = true }) { Icon(AppIcons.Import, contentDescription = "Import") }
                        if (state.tabs.isNotEmpty()) {
                            IconButton(onClick = { share("Satisfactory Tools export", exportTabs(state.tabs.toList())) }) {
                                Icon(AppIcons.Share, contentDescription = "Export all")
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
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
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
            // Lines grouped by folder; lines outside any folder come last.
            val groups: List<Folder?> = state.folders.toList() + listOf(null)
            for (folder in groups) {
                val lines = state.linesIn(folder)
                if (folder == null && lines.isEmpty() && state.folders.isNotEmpty()) continue
                if (state.folders.isNotEmpty()) {
                    item(key = "folder-" + (folder?.id ?: "none")) {
                        FolderHeader(
                            name = folder?.name ?: "Not in a folder",
                            lines = lines.size,
                            onOverview = { onFolder(folder?.id) },
                            onNewLine = folder?.let { f ->
                                {
                                    val base = state.newProductionData()
                                    onOpen(state.addTab(base.copy(metadata = base.metadata.copy(folder = f.id))).id)
                                }
                            },
                            onRename = folder?.let { f -> { naming = FolderNaming(f) } },
                            onDelete = folder?.let { f -> { state.deleteFolder(f) } },
                        )
                    }
                }
            items(lines, key = { it.id }) { tab ->
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
                        if (!selecting) TabMenu(state, tab, share, onOpen, onNewFolder = { naming = FolderNaming(null, tab) })
                    }
                }
            }
            }
            if (state.tabs.isNotEmpty()) {
                item(key = "new-folder") {
                    TextButton(onClick = { naming = FolderNaming(null) }) {
                        Icon(AppIcons.Folder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New folder for a project")
                    }
                }
            }
        }
    }
    naming?.let { n ->
        var name by remember(n) { mutableStateOf(n.folder?.name ?: "") }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (n.folder == null) "New folder" else "Rename folder") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, placeholder = { Text("e.g. Phase 4 megafactory") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    if (n.folder == null) {
                        val created = state.createFolder(name.trim())
                        n.moveLine?.let { state.moveToFolder(it, created) }
                    } else {
                        state.renameFolder(n.folder, name.trim())
                    }
                    naming = null
                }) { Text(if (n.folder == null) "Create" else "Rename") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
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

/** Creating ([folder] null) or renaming a folder; a line to move into the new one, if any. */
private data class FolderNaming(val folder: Folder?, val moveLine: ProductionTab? = null)

@Composable
private fun FolderHeader(
    name: String,
    lines: Int,
    onOverview: () -> Unit,
    onNewLine: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text("$lines line${if (lines == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOverview) { Text("Totals") }
        if (onNewLine != null || onRename != null || onDelete != null) {
            IconButton(onClick = { open = true }) { Icon(AppIcons.MoreVert, contentDescription = "Folder options") }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                onNewLine?.let { DropdownMenuItem(text = { Text("New line in this folder") }, leadingIcon = { Icon(AppIcons.Add, null) }, onClick = { open = false; it() }) }
                onRename?.let { DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = { open = false; it() }) }
                onDelete?.let {
                    DropdownMenuItem(text = { Text("Remove folder (keeps its lines)") }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { open = false; it() })
                }
            }
        }
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
private fun TabMenu(state: AppState, tab: ProductionTab, share: (String, String) -> Unit, onOpen: (Long) -> Unit, onNewFolder: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    // Order matters within a folder: lines take leftovers from the lines above them.
    val siblings = state.linesIn(state.folders.firstOrNull { it.id == tab.folderId })
    val position = siblings.indexOf(tab)
    IconButton(onClick = { open = true }) { Icon(AppIcons.MoreVert, contentDescription = "More") }
    DropdownMenu(open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Clone") }, leadingIcon = { Icon(AppIcons.Copy, null) }, onClick = { open = false; onOpen(state.cloneTab(tab).id) })
        DropdownMenuItem(text = { Text("Move to folder…") }, leadingIcon = { Icon(AppIcons.Folder, null) }, onClick = { open = false; moving = true })
        if (position > 0) {
            DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(AppIcons.ArrowUp, null) }, onClick = {
                open = false
                state.moveTab(state.tabs.indexOf(tab), state.tabs.indexOf(siblings[position - 1]))
            })
        }
        if (position in 0 until siblings.lastIndex) {
            DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(AppIcons.ArrowDown, null) }, onClick = {
                open = false
                state.moveTab(state.tabs.indexOf(tab), state.tabs.indexOf(siblings[position + 1]))
            })
        }
        DropdownMenuItem(text = { Text("Export") }, leadingIcon = { Icon(AppIcons.Share, null) }, onClick = { open = false; share(tab.name, exportTabs(listOf(tab))) })
        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { open = false; confirmRemove = true })
    }
    if (moving) {
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text("Move \"${tab.name}\" to") },
            text = {
                Column {
                    (state.folders.toList() + listOf(null)).forEach { folder ->
                        TextButton(onClick = { moving = false; state.moveToFolder(tab, folder) }, enabled = folder?.id != tab.folderId) {
                            Text(folder?.name ?: "No folder", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = { moving = false; onNewFolder() }) { Text("New folder…", modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = { TextButton(onClick = { moving = false }) { Text("Cancel") } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove \"${tab.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; state.removeTab(tab) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val readFile = rememberSftFileReader()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import production lines") },
        text = {
            Column {
                Text(
                    "Paste the contents of a .sft export (from this app or satisfactorytools.com)" + if (readFile != null) " or open the file." else ".",
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
                Row {
                    TextButton(onClick = { clipboard.getText()?.text?.let { text = it; error = null } }) { Text("Paste from clipboard") }
                    readFile?.let { read -> TextButton(onClick = { read()?.let { text = it; error = null } }) { Text("Open file…") } }
                }
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
