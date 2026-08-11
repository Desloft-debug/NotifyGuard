package com.guard.notifyguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Screen { HOME, DICT, LOG }

/* ---------------------------------- Каркас ---------------------------------- */

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var refresh by remember { mutableIntStateOf(0) }
    OnResume { refresh++ }

    var themeMode by remember(refresh) { mutableStateOf(prefs.themeMode) }
    var lang by remember(refresh) { mutableStateOf(prefs.lang) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    val systemDark = systemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val strings = when (lang) {
        Lang.RU -> RU
        Lang.EN -> EN
        Lang.SYSTEM -> if (Locale.getDefault().language.equals("ru", true)) RU else EN
    }

    GuardTheme(dark) {
        CompositionLocalProvider(LocalStrings provides strings) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 6 * offset } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 6 * offset } +
                            fadeOut(tween(160)))
                },
                label = "screen"
            ) { current ->
                when (current) {
                    Screen.HOME -> HomeScreen(
                        prefs = prefs,
                        refresh = refresh,
                        onRefresh = { refresh++ },
                        themeMode = themeMode,
                        onTheme = { themeMode = it; prefs.themeMode = it },
                        lang = lang,
                        onLang = { lang = it; prefs.lang = it },
                        onOpenDict = { screen = Screen.DICT },
                        onOpenLog = { screen = Screen.LOG }
                    )
                    Screen.DICT -> DictScreen(prefs) { screen = Screen.HOME }
                    Screen.LOG -> LogScreen(prefs) { screen = Screen.HOME }
                }
            }
        }
    }
}

@Composable
private fun systemInDarkTheme(): Boolean {
    val cfg = LocalConfiguration.current
    return (cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/* ------------------------------- Главный экран ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    prefs: Prefs,
    refresh: Int,
    onRefresh: () -> Unit,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    lang: Lang,
    onLang: (Lang) -> Unit,
    onOpenDict: () -> Unit,
    onOpenLog: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filterEnabled by remember(refresh) { mutableStateOf(prefs.filterEnabled) }
    var strictMode by remember(refresh) { mutableStateOf(prefs.strictMode) }
    var silenceCalls by remember(refresh) { mutableStateOf(prefs.silenceUnknownCalls) }
    var storeText by remember(refresh) { mutableStateOf(prefs.storeLogText) }
    var autoUpdate by remember(refresh) { mutableStateOf(prefs.updateCheckEnabled) }
    var allowed by remember(refresh) { mutableStateOf(prefs.allowedApps) }
    val wordsTotal = remember(refresh) {
        prefs.customBlockWords.size + prefs.customAllowWords.size
    }

    val listenerOn = remember(refresh) { GuardNotificationListener.isEnabled(context) }
    val screeningOn = remember(refresh) { isCallScreener(context) }
    val contactsOn = remember(refresh) { ContactsRepo.hasPermission(context) }

    var showPicker by remember { mutableStateOf(false) }

    // Состояние обновления
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    suspend fun check(manual: Boolean) {
        updateState = UpdateState.Checking
        prefs.lastUpdateCheck = System.currentTimeMillis()
        Updater.fetchLatest()
            .onSuccess { info ->
                updateState = if (Updater.isNewer(BuildConfig.VERSION_NAME, info.version)) {
                    UpdateState.Available(info)
                } else {
                    if (manual) UpdateState.UpToDate else UpdateState.Idle
                }
            }
            .onFailure { updateState = if (manual) UpdateState.Failed else UpdateState.Idle }
    }

    LaunchedEffect(Unit) {
        if (Updater.shouldCheck(prefs)) check(manual = false)
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(s.appTitle, style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = updateState is UpdateState.Available,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    (updateState as? UpdateState.Available)?.let { st ->
                        UpdateBanner(
                            info = st.info,
                            onDownload = {
                                scope.launch {
                                    updateState = UpdateState.Downloading(0f)
                                    Updater.download(context, st.info) { p ->
                                        updateState = UpdateState.Downloading(p)
                                    }.onSuccess { f ->
                                        updateState = UpdateState.Ready(f)
                                    }.onFailure { updateState = UpdateState.Failed }
                                }
                            }
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = updateState is UpdateState.Downloading ||
                        updateState is UpdateState.Ready,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DownloadCard(
                        state = updateState,
                        onInstall = { file ->
                            if (Updater.canInstall(context)) Updater.install(context, file)
                            else Updater.requestInstallPermission(context)
                        }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavTile(
                        title = s.openDict,
                        subtitle = "$wordsTotal ${s.wordsCount}",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDict
                    )
                    NavTile(
                        title = s.openLog,
                        subtitle = s.openLogHint,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenLog
                    )
                }
            }

            item {
                Section(s.accessTitle) {
                    StatusRow(
                        s.accessNotifications, listenerOn,
                        if (listenerOn) s.accessNotificationsOn else s.accessNotificationsOff,
                        s.actionOpenSettings
                    ) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    StatusRow(
                        s.accessCalls, screeningOn,
                        if (screeningOn) s.accessCallsOn else s.accessCallsOff,
                        s.actionAssign
                    ) {
                        requestScreeningRole(context)?.let { roleLauncher.launch(it) }
                    }
                    StatusRow(
                        s.accessContacts, contactsOn,
                        if (contactsOn) s.accessContactsOn else s.accessContactsOff,
                        s.actionGrant
                    ) {
                        contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }

            item {
                Section(s.notificationsTitle) {
                    SwitchRow(s.hideAds, s.hideAdsHint, filterEnabled) {
                        filterEnabled = it; prefs.filterEnabled = it
                    }
                    SwitchRow(s.strictMode, s.strictModeHint, strictMode) {
                        strictMode = it; prefs.strictMode = it
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${s.whitelistCount}: ${allowed.size}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FilledTonalButton(onClick = { showPicker = true }) {
                            Text(s.chooseApps)
                        }
                    }
                }
            }

            item {
                Section(s.callsTitle) {
                    SwitchRow(s.silenceUnknown, s.silenceUnknownHint, silenceCalls) {
                        silenceCalls = it; prefs.silenceUnknownCalls = it
                    }
                    AnimatedVisibility(
                        visible = silenceCalls && !screeningOn,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            s.callsWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Section(s.appearanceTitle) {
                    Text(s.theme, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        listOf(s.themeSystem, s.themeLight, s.themeDark),
                        themeMode.ordinal
                    ) { onTheme(ThemeMode.entries[it]) }
                    Spacer(Modifier.height(16.dp))
                    Text(s.language, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        listOf(s.langSystem, s.langRu, s.langEn),
                        lang.ordinal
                    ) { onLang(Lang.entries[it]) }
                }
            }

            item {
                Section(s.updateTitle) {
                    Text(
                        "${s.currentVersion}: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(s.updateAuto, s.updateAutoHint, autoUpdate) {
                        autoUpdate = it; prefs.updateCheckEnabled = it
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { scope.launch { check(manual = true) } },
                            enabled = updateState !is UpdateState.Checking
                        ) {
                            Text(
                                if (updateState is UpdateState.Checking) s.updateChecking
                                else s.updateCheck
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        AnimatedContent(updateState, label = "update") { st ->
                            val msg = when (st) {
                                is UpdateState.UpToDate -> s.updateUpToDate
                                is UpdateState.Failed -> s.updateFailed
                                else -> ""
                            }
                            if (msg.isNotEmpty()) {
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        s.updateNetworkNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Section(s.privacyTitle) {
                    SwitchRow(s.storeText, s.storeTextHint, storeText) {
                        storeText = it; prefs.storeLogText = it
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            prefs = prefs,
            selected = allowed,
            onToggle = { prefs.toggleAllowed(it); allowed = prefs.allowedApps },
            onDismiss = { showPicker = false }
        )
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val file: File) : UpdateState
}

@Composable
private fun UpdateBanner(info: ReleaseInfo, onDownload: () -> Unit) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "${s.updateFound} ${info.tag}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    info.notes.lines().take(4).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDownload, shape = RoundedCornerShape(14.dp)) {
                Text(s.updateDownload)
            }
        }
    }
}

@Composable
private fun DownloadCard(state: UpdateState, onInstall: (File) -> Unit) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            when (state) {
                is UpdateState.Downloading -> {
                    val p by animateFloatAsState(state.progress, label = "progress")
                    Text(s.updateDownloading, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    )
                }
                is UpdateState.Ready -> {
                    Button(
                        onClick = { onInstall(state.file) },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(s.updateInstall) }
                }
                else -> Unit
            }
        }
    }
}

/* -------------------------------- Экран словаря ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictScreen(prefs: Prefs, onBack: () -> Unit) {
    val s = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }
    var blockWords by remember { mutableStateOf(prefs.customBlockWords) }
    var allowWords by remember { mutableStateOf(prefs.customAllowWords) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(s.dictTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text(s.tabStop) })
                Tab(tab == 1, { tab = 1 }, text = { Text(s.tabAllow) })
                Tab(tab == 2, { tab = 2 }, text = { Text(s.tabBuiltIn) })
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "dict"
            ) { current ->
                when (current) {
                    0 -> WordList(
                        hint = s.stopWordsHint,
                        placeholder = s.stopWordsPlaceholder,
                        words = blockWords,
                        onAdd = { prefs.addBlockWord(it); blockWords = prefs.customBlockWords },
                        onRemove = { prefs.removeBlockWord(it); blockWords = prefs.customBlockWords }
                    )
                    1 -> WordList(
                        hint = s.allowWordsHint,
                        placeholder = s.allowWordsPlaceholder,
                        words = allowWords,
                        onAdd = { prefs.addAllowWord(it); allowWords = prefs.customAllowWords },
                        onRemove = { prefs.removeAllowWord(it); allowWords = prefs.customAllowWords }
                    )
                    else -> BuiltInList()
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

@Composable
private fun BuiltInList() {
    val s = LocalStrings.current
    val groups = listOf(
        s.groupEmergency to FilterRules.EMERGENCY_WORDS,
        s.groupSystem to FilterRules.SYSTEM_WORDS,
        s.groupDelivery to FilterRules.DELIVERY_WORDS,
        s.groupCode to FilterRules.CODE_WORDS,
        s.groupMoney to FilterRules.MONEY_WORDS,
        s.groupPromo to FilterRules.PROMO_WORDS
    )
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                            "${words.size} ${s.wordsCount}",
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

/* -------------------------------- Экран журнала ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogScreen(prefs: Prefs, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val notifications = remember(version) { GuardLog.readNotifications(context) }
    val calls = remember(version) { GuardLog.readCalls(context) }
    var allowed by remember(version) { mutableStateOf(prefs.allowedApps) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(s.logTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (tab == 0) GuardLog.clearNotifications(context)
                        else GuardLog.clearCalls(context)
                        version++
                    }) { Text(s.clear) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text(s.tabNotifications) })
                Tab(tab == 1, { tab = 1 }, text = { Text(s.tabCalls) })
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
                        if (notifications.isEmpty()) {
                            item { EmptyNote(s.emptyNotifications) }
                        } else {
                            items(notifications) { e ->
                                LogCard {
                                    Text(
                                        appLabel(context, e.pkg),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        e.title.ifBlank { s.hiddenText },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${e.timeText()} · ${e.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (e.pkg !in allowed) {
                                        TextButton(
                                            onClick = {
                                                prefs.toggleAllowed(e.pkg)
                                                allowed = prefs.allowedApps
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) { Text(s.unwhitelist) }
                                    }
                                }
                            }
                        }
                    } else {
                        if (calls.isEmpty()) {
                            item { EmptyNote(s.emptyCalls) }
                        } else {
                            items(calls) { c ->
                                LogCard {
                                    Text(
                                        c.number.ifBlank { "—" },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "${c.timeText()} · ${s.silenced}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun LogCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

/* ------------------------------- Общие элементы ----------------------------- */

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun NavTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                if (title == LocalStrings.current.openLog) Icons.Filled.List
                else Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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

@Composable
private fun SwitchRow(
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
private fun StatusRow(
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

@Composable
private fun SegmentedRow(
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
private fun AppPickerDialog(
    prefs: Prefs,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

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

@Composable
private fun OnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) action()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

private fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
}.getOrDefault(pkg)

private fun isCallScreener(context: Context): Boolean {
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
        rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private fun requestScreeningRole(context: Context): Intent? {
    val rm = context.getSystemService(RoleManager::class.java) ?: return null
    if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
    return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
