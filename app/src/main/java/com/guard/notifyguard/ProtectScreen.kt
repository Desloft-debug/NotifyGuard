package com.guard.notifyguard

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Главная вкладка: доступы, переключатели фильтра, обновление, копия настроек.

@Composable
internal fun ProtectScreen(
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

    val access = rememberAccess(refresh)
    val listenerOn = access.listener
    val screeningOn = access.screening
    val contactsOn = access.contacts

    val batteryFree = remember(refresh) { KeepAlive.isBatteryUnrestricted(context) }
    val hasVendor = remember { KeepAlive.hasVendorScreen(context) }
    var keepAlive by remember(refresh) { mutableStateOf(prefs.keepAlive) }

    var showPicker by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var needInstallPermission by remember { mutableStateOf(false) }

    // manual = человек нажал кнопку сам, значит ответить надо в любом случае,
    // даже если обновления нет
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

    // разовые дела на вход: поднять слушатель и сервис, сходить за словарём и обновлением
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
    // Колбэк лаунчера приходит в главный поток, а провайдер SAF (тот же Google Диск)
    // может думать секундами — поэтому файл читаем и пишем в IO.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { Backup.write(context, uri, prefs) }
                .onSuccess { notify(s.backupSaved) }
                .onFailure { notify(s.backupFailed) }
        }
    }
    val loadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { Backup.read(context, uri, prefs) }
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
                    updateState is UpdateState.Ready ||
                    updateState is UpdateState.Failed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DownloadCard(updateState) { file ->
                    scope.launch {
                        when {
                            // сначала объясняем, зачем выкидываем в настройки
                            !Updater.canInstall(context) -> needInstallPermission = true
                            // разбор APK — это чтение файла, в главном потоке не надо
                            !withContext(Dispatchers.IO) {
                                Updater.signatureMatches(context, file)
                            } -> notify(s.updateSignatureMismatch)
                            else -> Updater.install(context, file)
                        }
                    }
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
                                openSettings(
                                    context,
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
                    openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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

    if (needInstallPermission) {
        AlertDialog(
            onDismissRequest = { needInstallPermission = false },
            title = { Text(s.updateNeedPermission) },
            text = { Text(s.updateNeedPermissionHint) },
            confirmButton = {
                TextButton(onClick = {
                    needInstallPermission = false
                    Updater.requestInstallPermission(context)
                }) { Text(s.updateGrant) }
            },
            dismissButton = {
                TextButton(onClick = { needInstallPermission = false }) { Text(s.cancel) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
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
                // обрыв связи должен быть виден, иначе кнопка просто «не работает»
                is UpdateState.Failed -> {
                    Text(
                        s.updateDownloadFailed,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> Unit
            }
        }
    }
}
