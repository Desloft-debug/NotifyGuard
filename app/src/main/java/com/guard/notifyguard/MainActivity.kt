package com.guard.notifyguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
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

private enum class Tab(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PROTECT(Icons.Filled.Lock),
    DICT(Icons.Filled.Search),
    LOG(Icons.Filled.List),
    HELP(Icons.Filled.Info)
}

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var refresh by remember { mutableIntStateOf(0) }
    OnResume { refresh++ }

    var themeMode by remember(refresh) { mutableStateOf(prefs.themeMode) }
    var lang by remember(refresh) { mutableStateOf(prefs.lang) }
    var tab by remember { mutableStateOf(Tab.PROTECT) }
    var numberDialog by remember { mutableStateOf<String?>(null) }
    var regionChosen by remember { mutableStateOf(prefs.regionChosen) }

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
            if (!regionChosen) {
                WelcomeScreen(
                    initial = prefs.region,
                    lang = lang,
                    onLang = { lang = it; prefs.lang = it },
                    onDone = { chosen ->
                        prefs.region = chosen
                        prefs.regionChosen = true
                        regionChosen = true
                    }
                )
                return@CompositionLocalProvider
            }
            MainScaffold(
                prefs = prefs,
                refresh = refresh,
                onRefresh = { refresh++ },
                tab = tab,
                onTab = { tab = it },
                themeMode = themeMode,
                onTheme = { themeMode = it; prefs.themeMode = it },
                lang = lang,
                onLang = { lang = it; prefs.lang = it },
                onOpenNumber = { numberDialog = it }
            )
            numberDialog?.let { num ->
                NumberDialog(num) { numberDialog = null }
            }
        }
    }
}

/** Первый запуск: выбор языка интерфейса и региона рекламного словаря. */
@Composable
private fun WelcomeScreen(
    initial: Region,
    lang: Lang,
    onLang: (Lang) -> Unit,
    onDone: (Region) -> Unit
) {
    val s = LocalStrings.current
    var region by remember { mutableStateOf(initial) }

    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp, 48.dp, 24.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(s.welcomeTitle, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    s.welcomeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                SegmentedRow(
                    listOf(s.langSystem, s.langRu, s.langEn), lang.ordinal
                ) { onLang(Lang.entries[it]) }
                Spacer(Modifier.height(24.dp))
                Text(s.regionTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    s.regionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            items(
                listOf(
                    Triple(Region.RU, s.regionRu, s.regionRuHint),
                    Triple(Region.EN, s.regionEn, s.regionEnHint),
                    Triple(Region.ALL, s.regionAll, s.regionAllHint)
                )
            ) { (value, title, hint) ->
                RegionCard(
                    title = title,
                    hint = hint,
                    count = Dictionaries.promoSize(value),
                    selected = region == value
                ) { region = value }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    s.regionNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onDone(region) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.continueBtn) }
            }
        }
    }
}

@Composable
private fun RegionCard(
    title: String,
    hint: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "regionbg"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    prefs: Prefs,
    refresh: Int,
    onRefresh: () -> Unit,
    tab: Tab,
    onTab: (Tab) -> Unit,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    lang: Lang,
    onLang: (Lang) -> Unit,
    onOpenNumber: (String) -> Unit
) {
    val s = LocalStrings.current
    val titles = mapOf(
        Tab.PROTECT to s.navProtect,
        Tab.DICT to s.dictTitle,
        Tab.LOG to s.logTitle,
        Tab.HELP to s.navHelp
    )
    val labels = mapOf(
        Tab.PROTECT to s.navProtect,
        Tab.DICT to s.navDict,
        Tab.LOG to s.navLog,
        Tab.HELP to s.navHelp
    )

    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == Tab.PROTECT) s.appTitle else titles[tab].orEmpty(),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon, contentDescription = labels[t]) },
                        label = { Text(labels[t].orEmpty(), maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(240)) { it / 8 * dir } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 8 * dir } + fadeOut(tween(140)))
            },
            label = "tabs"
        ) { current ->
            when (current) {
                Tab.PROTECT -> ProtectScreen(
                    prefs, refresh, onRefresh, themeMode, onTheme, lang, onLang,
                    notify = { msg -> scope.launch { snackbarState.showSnackbar(msg) } }
                )
                Tab.DICT -> DictScreen(prefs)
                Tab.LOG -> LogScreen(prefs, onOpenNumber)
                Tab.HELP -> HelpScreen(
                    prefs, refresh, onRefresh,
                    notify = { msg -> scope.launch { snackbarState.showSnackbar(msg) } }
                )
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

@Composable
private fun ProtectScreen(
    prefs: Prefs,
    refresh: Int,
    onRefresh: () -> Unit,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    lang: Lang,
    onLang: (Lang) -> Unit,
    notify: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var filterEnabled by remember(refresh) { mutableStateOf(prefs.filterEnabled) }
    var strictMode by remember(refresh) { mutableStateOf(prefs.strictMode) }
    var silenceCalls by remember(refresh) { mutableStateOf(prefs.silenceUnknownCalls) }
    var storeText by remember(refresh) { mutableStateOf(prefs.storeLogText) }
    var autoUpdate by remember(refresh) { mutableStateOf(prefs.updateCheckEnabled) }
    var allowed by remember(refresh) { mutableStateOf(prefs.allowedApps) }
    val blockedCount = remember(refresh) { prefs.blockedApps.size }
    var region by remember(refresh) { mutableStateOf(prefs.region) }

    val listenerOn = remember(refresh) { GuardNotificationListener.isPermitted(context) }
    val batteryFree = remember(refresh) { KeepAlive.isBatteryUnrestricted(context) }
    val hasVendor = remember { KeepAlive.hasVendorScreen(context) }
    var keepAlive by remember(refresh) { mutableStateOf(prefs.keepAlive) }
    val screeningOn = remember(refresh) { isCallScreener(context) }
    val contactsOn = remember(refresh) { ContactsRepo.hasPermission(context) }

    var showPicker by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    suspend fun check(manual: Boolean) {
        updateState = UpdateState.Checking
        prefs.lastUpdateCheck = System.currentTimeMillis()
        Updater.fetchLatest()
            .onSuccess { info ->
                if (Updater.isNewer(BuildConfig.VERSION_NAME, info.version)) {
                    updateState = UpdateState.Available(info)
                    if (manual) {
                        notify("${s.updateFound} ${info.tag}")
                        listState.animateScrollToItem(0)
                    }
                } else {
                    updateState = UpdateState.Idle
                    if (manual) notify(s.updateUpToDate)
                }
            }
            .onFailure {
                updateState = UpdateState.Idle
                if (manual) notify(s.updateFailed)
            }
    }

    LaunchedEffect(Unit) {
        GuardNotificationListener.ensureBound(context)
        KeepAlive.schedule(context)
        GuardForegroundService.start(context)
        if (RemoteDictionary.shouldSync(prefs)) RemoteDictionary.sync(prefs)
        if (Updater.shouldCheck(prefs)) check(manual = false)
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            Backup.write(context, uri, prefs)
                .onSuccess { notify(s.backupSaved) }
                .onFailure { notify(s.backupFailed) }
        }
    }
    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Backup.read(context, uri, prefs)
                .onSuccess { notify(s.backupLoaded); onRefresh() }
                .onFailure { notify(s.backupFailed) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = updateState is UpdateState.Available,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                (updateState as? UpdateState.Available)?.let { st ->
                    UpdateBanner(st.info) {
                        scope.launch {
                            updateState = UpdateState.Downloading(0f)
                            Updater.download(context, st.info) { p ->
                                updateState = UpdateState.Downloading(p)
                            }.onSuccess { updateState = UpdateState.Ready(it) }
                                .onFailure { updateState = UpdateState.Failed }
                        }
                    }
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
                DownloadCard(updateState) { file ->
                    if (Updater.canInstall(context)) Updater.install(context, file)
                    else Updater.requestInstallPermission(context)
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = !listenerOn,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            s.accessNotificationsOff,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(s.actionOpenSettings) }
                    }
                }
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
                ) { requestScreeningRole(context)?.let { roleLauncher.launch(it) } }
                StatusRow(
                    s.accessContacts, contactsOn,
                    if (contactsOn) s.accessContactsOn else s.accessContactsOff,
                    s.actionGrant
                ) { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
            }
        }

        item {
            Section(s.backgroundTitle) {
                Text(
                    s.backgroundHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusRow(
                    s.batteryUnrestricted, batteryFree,
                    if (batteryFree) s.batteryUnrestrictedOn else s.batteryUnrestrictedOff,
                    s.batteryAction
                ) { KeepAlive.requestBatteryExemption(context) }

                if (hasVendor) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(s.autostart, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                s.autostartHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { KeepAlive.openVendorScreen(context) }) {
                            Text(s.autostartAction)
                        }
                    }
                }

                SwitchRow(s.keepAlive, s.keepAliveHint, keepAlive) {
                    keepAlive = it
                    prefs.keepAlive = it
                    if (it) GuardForegroundService.start(context)
                    else GuardForegroundService.stop(context)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    s.recentsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s.backgroundNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        "${s.whitelistCount}: ${allowed.size}   ·   " +
                            "${s.blockedAppsCount}: ${blockedCount}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(onClick = { showPicker = true }) { Text(s.chooseApps) }
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
            Section(s.regionTitle) {
                Text(
                    s.regionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                SegmentedRow(
                    listOf(s.regionRu, s.regionEn, s.regionAll), region.ordinal
                ) {
                    region = Region.entries[it]
                    prefs.region = region
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${s.groupPromo}: ${Dictionaries.promoSize(region)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Section(s.appearanceTitle) {
                Text(s.theme, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedRow(
                    listOf(s.themeSystem, s.themeLight, s.themeDark), themeMode.ordinal
                ) { onTheme(ThemeMode.entries[it]) }
                Spacer(Modifier.height(16.dp))
                Text(s.language, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedRow(
                    listOf(s.langSystem, s.langRu, s.langEn), lang.ordinal
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
                }
            }
        }

        item {
            Section(s.backupTitle) {
                Text(
                    s.backupHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { saveLauncher.launch(Backup.fileName()) }) {
                        Text(s.backupSave)
                    }
                    FilledTonalButton(onClick = { loadLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Text(s.backupLoad)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    s.backupNote,
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
                Spacer(Modifier.height(6.dp))
                Text(
                    s.updateNetworkNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun HelpScreen(
    prefs: Prefs,
    refresh: Int,
    onRefresh: () -> Unit,
    notify: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var hidden by remember(refresh) { mutableStateOf(prefs.onboardingDone) }
    val listenerOn = remember(refresh) { GuardNotificationListener.isPermitted(context) }
    val screeningOn = remember(refresh) { isCallScreener(context) }
    val contactsOn = remember(refresh) { ContactsRepo.hasPermission(context) }
    val allDone = listenerOn && screeningOn && contactsOn

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.setupTitle,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = {
                    hidden = !hidden
                    prefs.onboardingDone = hidden
                }) { Text(if (hidden) s.setupShow else s.setupHide) }
            }
        }

        if (hidden) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (allDone)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (allDone) Icons.Filled.Check else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (allDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (allDone) s.setupDone else s.setupIntro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (allDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    s.setupIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SetupStep(1, s.setupStep1, s.setupStep1Text, listenerOn, s.actionOpenSettings) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            item {
                SetupStep(2, s.setupStep2, s.setupStep2Text, contactsOn, s.actionGrant) {
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            item {
                SetupStep(3, s.setupStep3, s.setupStep3Text, screeningOn, s.actionAssign) {
                    requestScreeningRole(context)?.let { roleLauncher.launch(it) }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.setupRestricted, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.setupRestrictedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = { hidden = true; prefs.onboardingDone = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s.setupHide) }
            }
        }

        item { FeedbackCard(notify) }
    }
}

@Composable
private fun FeedbackCard(notify: (String) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var kind by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(true) }

    val titles = listOf(s.feedbackKindBug, s.feedbackKindIdea)

    fun body(): String = buildString {
        appendLine(text.trim())
        if (attach) {
            appendLine()
            append(Feedback.diagnostics())
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                s.feedbackTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                s.feedbackHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SegmentedRow(titles, kind) { kind = it }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                placeholder = { Text(s.feedbackPlaceholder) },
                shape = RoundedCornerShape(14.dp),
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(s.feedbackInclude, s.feedbackIncludeHint, attach) { attach = it }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val title = "[${titles[kind]}] " + text.trim().take(60)
                        val url = Feedback.issueUrl(title, body())
                        if (!Feedback.open(context, url)) {
                            Feedback.copy(context, body())
                            notify(s.feedbackNoBrowser)
                        }
                    },
                    enabled = text.trim().length >= 5,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(s.feedbackSend) }
                Spacer(Modifier.width(10.dp))
                TextButton(
                    onClick = {
                        Feedback.copy(context, body())
                        notify(s.feedbackCopied)
                    },
                    enabled = text.trim().isNotEmpty()
                ) { Text(s.feedbackCopy) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                s.feedbackNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    text: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bg by animateColorAsState(
                    if (done) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "stepdot"
                )
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("$number", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(!done) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                        Text(action)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberDialog(number: String, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val history = remember { GuardLog.readCalls(context) }
    val info = remember(number) { NumberLookup.analyze(number, history) }

    val riskText = when (info.risk) {
        RiskLevel.LOW -> s.riskLow
        RiskLevel.MEDIUM -> s.riskMedium
        RiskLevel.HIGH -> s.riskHigh
        RiskLevel.UNKNOWN -> s.riskUnknown
    }
    val riskColor = when (info.risk) {
        RiskLevel.LOW -> MaterialTheme.colorScheme.secondary
        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        RiskLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val kindText = when (info.kind) {
        NumberKind.MOBILE -> s.kindMobile
        NumberKind.LANDLINE -> s.kindLandline
        NumberKind.TOLL_FREE -> s.kindTollFree
        NumberKind.PREMIUM -> s.kindPremium
        NumberKind.SHORT -> s.kindShort
        NumberKind.HIDDEN -> s.kindHidden
        NumberKind.FOREIGN -> s.kindForeign
        NumberKind.UNKNOWN -> s.kindUnknown
    }
    val signalText = mapOf(
        "premium" to s.sigPremium, "tollfree" to s.sigTollFree,
        "landline" to s.sigLandline, "foreign" to s.sigForeign,
        "repeated" to s.sigRepeated, "blockcalling" to s.sigBlockCalling,
        "length" to s.sigLength, "hidden" to s.sigHidden, "short" to s.sigShort
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.done) } },
        shape = RoundedCornerShape(20.dp),
        title = { Text(info.raw.ifBlank { s.kindHidden }) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = .12f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                s.numberRisk,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                riskText,
                                style = MaterialTheme.typography.titleSmall,
                                color = riskColor
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    InfoRow(s.numberKind, kindText)
                    if (info.country.isNotBlank()) InfoRow(s.numberCountry, info.country)
                    if (info.region.isNotBlank()) InfoRow(s.numberRegion, info.region)
                    if (info.operator.isNotBlank()) InfoRow(s.numberOperator, info.operator)
                    Spacer(Modifier.height(10.dp))
                }
                items(info.signals) { key ->
                    signalText[key]?.let {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("• ", style = MaterialTheme.typography.bodySmall)
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.numberDisclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.numberAddContact,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (info.e164.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(14.dp))
                        Text(s.numberCheckOnline, style = MaterialTheme.typography.titleSmall)
                        Text(
                            s.numberCheckWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(NumberLookup.lookupLinks(info.e164)) { (name, url) ->
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(name) }
                    }
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun DictScreen(prefs: Prefs) {
    val s = LocalStrings.current
    var tab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }
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

/** Онлайн-словарь и встроенные списки на одной вкладке. */
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

    val region = remember { prefs.region }
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

@Composable
private fun LogScreen(prefs: Prefs, onOpenNumber: (String) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val notifications = remember(version) { GuardLog.readNotifications(context) }
    val calls = remember(version) { GuardLog.readCalls(context) }
    var allowed by remember(version) { mutableStateOf(prefs.allowedApps) }
    var blocked by remember(version) { mutableStateOf(prefs.blockedApps) }
    var expanded by remember { mutableStateOf<String?>(null) }

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
                version++
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
                            "${e.timeText()} · ${e.reason}",
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
                if (!blocked && !allowed) {
                    TextButton(onClick = onAllow, contentPadding = PaddingValues(0.dp)) {
                        Text(s.unwhitelist)
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

private fun openAppNotificationSettings(context: Context, pkg: String) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(fallback) } }
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
