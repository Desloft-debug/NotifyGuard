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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Вкладка словарей: свои стоп-слова и исключения плюс встроенные списки.

@Composable
internal fun DictScreen(prefs: Prefs) {
    val s = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }   // 0 - стоп-слова, 1 - исключения
    var blockWords by remember { mutableStateOf(prefs.customBlockWords) }
    var allowWords by remember { mutableStateOf(prefs.customAllowWords) }

    Column(Modifier.fillMaxSize()) {
        TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text(s.tabMine, maxLines = 1) })
            Tab(tab == 1, { tab = 1 }, text = { Text(s.tabReady, maxLines = 1) })
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "dict"
        ) { current ->
            if (current == 0) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
                        SegmentedRow(listOf(s.modeBlock, s.modeAllow), mode) { mode = it }
                    }
                    if (mode == 0) {
                        WordList(
                            hint = s.stopWordsHint,
                            placeholder = s.stopWordsPlaceholder,
                            words = blockWords,
                            onAdd = { prefs.addBlockWord(it); blockWords = prefs.customBlockWords },
                            onRemove = { prefs.removeBlockWord(it); blockWords = prefs.customBlockWords }
                        )
                    } else {
                        WordList(
                            hint = s.allowWordsHint,
                            placeholder = s.allowWordsPlaceholder,
                            words = allowWords,
                            onAdd = { prefs.addAllowWord(it); allowWords = prefs.customAllowWords },
                            onRemove = { prefs.removeAllowWord(it); allowWords = prefs.customAllowWords }
                        )
                    }
                }
            } else {
                ReadyWordsTab(prefs)
            }
        }
    }
}

/** Онлайн-словарь и встроенные списки — на одной вкладке. */
@Composable
private fun ReadyWordsTab(prefs: Prefs) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(prefs.remoteDictEnabled) }
    var dict by remember { mutableStateOf(RemoteDictionary.cached(prefs)) }
    var fetched by remember { mutableLongStateOf(prefs.remoteDictFetched) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val region = remember { prefs.region }   // на этой вкладке не меняется
    val groups = listOf(
        s.groupEmergency to Dictionaries.EMERGENCY,
        s.groupSystem to Dictionaries.SYSTEM,
        s.groupDelivery to Dictionaries.DELIVERY,
        s.groupCode to Dictionaries.CODE,
        s.groupMoney to Dictionaries.MONEY,
        s.groupPromoRegion to Dictionaries.promoFor(region)
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.onlineTitle, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.onlineHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(s.onlineEnabled, s.onlineEnabledHint, enabled) {
                        enabled = it
                        prefs.remoteDictEnabled = it
                        dict = RemoteDictionary.cached(prefs)
                    }
                    AnimatedVisibility(enabled) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${s.onlineVersion}: ${dict.version} · " +
                                    "${dict.block.size} ${s.onlineBlocked}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${s.onlineUpdated}: " + if (fetched == 0L) s.onlineNever
                                else LogEntry("", "", "", fetched).timeText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true; failed = false
                                            RemoteDictionary.sync(prefs)
                                                .onSuccess {
                                                    dict = it
                                                    fetched = prefs.remoteDictFetched
                                                }
                                                .onFailure { failed = true }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy
                                ) { Text(if (busy) s.onlineSyncing else s.onlineSync) }
                                Spacer(Modifier.width(12.dp))
                                AnimatedVisibility(failed) {
                                    Text(
                                        s.onlineFailed,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                s.onlineSafety,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                s.builtInHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(groups, key = { it.first }) { (title, words) ->
            val open = expanded == title
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                    .clickable { expanded = if (open) null else title }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${words.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (open) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            words.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordList(
    hint: String,
    placeholder: String,
    words: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val s = LocalStrings.current
    var input by remember { mutableStateOf("") }
    val sorted = remember(words) { words.sorted() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 40) input = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onAdd(input); input = "" },
                    enabled = input.trim().length >= 2,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(s.add) }
            }
        }
        if (sorted.isEmpty()) {
            item {
                Text(
                    s.emptyList,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(sorted, key = { it }) { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRemove(w) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = s.remove,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Text(
                    s.wordMatchHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
