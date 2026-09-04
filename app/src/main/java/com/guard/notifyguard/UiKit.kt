package com.guard.notifyguard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Мелочи, из которых собраны экраны.

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun StatusRow(
    title: String,
    ok: Boolean,
    hint: String,
    action: String,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    val dotColor by animateColorAsState(
        if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        label = "dot"
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$title · ${if (ok) s.enabled else s.disabled}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(!ok, enter = fadeIn(), exit = fadeOut()) {
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

// Свой сегментный переключатель: у material3 он всё ещё экспериментальный,
// а нужна тут ровно одна строка кнопок.
@Composable
internal fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(220),
                label = "seg"
            )
            val fg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(220),
                label = "segText"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = fg
                )
            }
        }
    }
}

@Composable
internal fun AppPickerDialog(
    prefs: Prefs,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // Собираем один раз на открытие: при вводе в поиск список пересобираться не должен,
    // getApplicationLabel на пару сотен пакетов заметно тормозит.
    val apps = remember {
        (prefs.seenApps + selected).distinct()
            .map { it to appLabel(context, it) }
            .sortedBy { it.second.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.done) } },
        title = { Text(s.pickerTitle) },
        shape = RoundedCornerShape(20.dp),
        text = {
            Column {
                Text(
                    s.pickerHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(s.search) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                if (apps.isEmpty()) {
                    Text(s.pickerEmpty, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val filtered = apps.filter {
                        it.second.contains(query, true) || it.first.contains(query, true)
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggle(pkg) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (pkg in selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                                AnimatedVisibility(pkg in selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
