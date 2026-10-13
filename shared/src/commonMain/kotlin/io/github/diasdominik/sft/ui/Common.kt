package io.github.diasdominik.sft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.diasdominik.sft.data.GameData
import io.github.diasdominik.sft.data.ItemAmount
import io.github.diasdominik.sft.planner.Numbers

fun fmt(value: Double, decimals: Int = 3) = Numbers.format(value, decimals)

/** Icon for an item, building or schematic; see [GameIcon]. */
@Composable
fun EntityIcon(data: GameData?, className: String?, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    GameIcon(data, className, size, modifier)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            actions()
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            content()
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
fun EntityRow(
    data: GameData?,
    className: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityIcon(data, className, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(data?.nameOf(className) ?: className, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** "12x [icon] Iron Plate" chip used for recipe ingredients, costs and products. */
@Composable
fun AmountChip(
    data: GameData?,
    amount: ItemAmount,
    perMinute: Double? = null,
    onClick: ((String) -> Unit)? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable { onClick(amount.item) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmt(amount.amount) + "×", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(6.dp))
        EntityIcon(data, amount.item, 22.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(data?.nameOf(amount.item) ?: amount.item, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (perMinute != null) {
                Text(fmt(perMinute) + " / min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountChips(data: GameData?, amounts: List<ItemAmount>, perMinute: ((ItemAmount) -> Double?)? = null, onClick: ((String) -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        amounts.forEach { AmountChip(data, it, perMinute?.invoke(it), onClick) }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(AppIcons.Close, contentDescription = "Clear") }
        },
    )
}

/** Numeric text field that keeps the user's partial input (e.g. "1.") while typing. */
@Composable
fun NumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    suffix: String? = null,
) {
    var text by remember { mutableStateOf(plain(value)) }
    var focused by remember { mutableStateOf(false) }
    var ownChange by remember { mutableStateOf(false) }
    // Resync when the value changes from outside (e.g. "Set to 0"). While typing, a value the parent
    // clamped or rejected is left alone and shown once the field loses focus.
    LaunchedEffect(value) {
        if (focused && ownChange) {
            ownChange = false
            return@LaunchedEffect
        }
        val typed = text.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (kotlin.math.abs(typed - value) > 1e-9) text = plain(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() || it == '.' || it == ',' }
            text = cleaned
            val number = if (cleaned.isEmpty()) 0.0 else cleaned.replace(",", ".").toDoubleOrNull()
            if (number != null) {
                ownChange = true
                onValueChange(number)
            }
        },
        modifier = modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) {
                ownChange = false
                text = plain(value)
            }
            focused = state.isFocused
        },
        enabled = enabled,
        singleLine = true,
        label = label?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** Whole-number field where an empty box means "not set". */
@Composable
fun OptionalCountField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value?.toString() ?: ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val cleaned = new.filter { it.isDigit() }.take(4)
            text = cleaned
            onValueChange(cleaned.toIntOrNull())
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private fun plain(value: Double) = Numbers.format(value, 6).replace(",", "")

/** Searchable picker dialog for items (or any class names with names in [data]). */
@Composable
fun EntityPickerDialog(
    data: GameData,
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        options.filter { data.nameOf(it).contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SearchField(query, { query = it }, "Search")
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it }) { className ->
                        EntityRow(data, className, onClick = { onPick(className) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
