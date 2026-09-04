package com.guard.notifyguard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Журнал: что скрыли и какие звонки приглушили.

@Composable
internal fun LogScreen(prefs: Prefs, onOpenNumber: (String) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val notifications = remember(version) { GuardLog.readNotifications(context) }
    val calls = remember(version) { GuardLog.readCalls(context) }
    var allowed by remember(version) { mutableStateOf(prefs.allowedApps) }
    var blocked by remember(version) { mutableStateOf(prefs.blockedApps) }
    var expanded by remember { mutableStateOf<String?>(null) }

    // одна карточка на приложение, самые «шумные» сверху
    val groups = remember(notifications) {
        notifications.groupBy { it.pkg }
            .map { (pkg, items) -> pkg to items }
            .sortedByDescending { it.second.size }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                tab,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(tab == 0, { tab = 0 }, text = { Text(s.tabNotifications) })
                Tab(tab == 1, { tab = 1 }, text = { Text(s.tabCalls) })
            }
            TextButton(onClick = {
                if (tab == 0) GuardLog.clearNotifications(context)
                else GuardLog.clearCalls(context)
                version++   // без этого список останется на экране
            }) { Text(s.clear) }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "log"
        ) { current ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (current == 0) {
                    if (groups.isEmpty()) {
                        item { EmptyNote(s.emptyNotifications) }
                    } else {
                        items(groups, key = { it.first }) { (pkg, items) ->
                            AppGroupCard(
                                pkg = pkg,
                                items = items,
                                open = expanded == pkg,
                                allowed = pkg in allowed,
                                blocked = pkg in blocked,
                                onToggleOpen = { expanded = if (expanded == pkg) null else pkg },
                                onAllow = {
                                    prefs.toggleAllowed(pkg)
                                    allowed = prefs.allowedApps
                                    blocked = prefs.blockedApps
                                },
                                onBlock = {
                                    prefs.toggleBlocked(pkg)
                                    blocked = prefs.blockedApps
                                    allowed = prefs.allowedApps
                                },
                                onSettings = { openAppNotificationSettings(context, pkg) }
                            )
                        }
                    }
                } else {
                    if (calls.isEmpty()) {
                        item { EmptyNote(s.emptyCalls) }
                    } else {
                        items(calls) { c ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenNumber(c.number) }
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            c.number.ifBlank { s.kindHidden },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "${c.timeText()} · ${s.silenced}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = s.numberTitle,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGroupCard(
    pkg: String,
    items: List<LogEntry>,
    open: Boolean,
    allowed: Boolean,
    blocked: Boolean,
    onToggleOpen: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onSettings: () -> Unit
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val label = remember(pkg) { appLabel(context, pkg) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (blocked) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggleOpen() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${items.size} ${s.logGroupCount} · ${items.first().timeText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (blocked) {
                    Text(
                        s.blockAppDone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (open) {
                Spacer(Modifier.height(12.dp))
                items.take(20).forEach { e ->
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            e.title.ifBlank { s.hiddenText },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${e.timeText()} · ${ReasonText.render(e.reason, lang)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(onClick = onBlock) {
                        Text(if (blocked) s.unblockApp else s.blockApp)
                    }
                    TextButton(onClick = onSettings) { Text(s.appSettings) }
                }
                if (!blocked) {
                    Text(
                        s.blockAppWarn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // и добавить в белый список, и убрать из него — одной кнопкой
                if (!blocked) {
                    TextButton(onClick = onAllow, contentPadding = PaddingValues(0.dp)) {
                        Text(if (allowed) s.rewhitelist else s.unwhitelist)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
