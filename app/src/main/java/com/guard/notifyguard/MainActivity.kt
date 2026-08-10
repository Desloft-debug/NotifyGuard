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
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Screen { HOME, LOG }

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var refresh by remember { mutableIntStateOf(0) }
    OnResume { refresh++ }

    var themeMode by remember(refresh) { mutableStateOf(prefs.themeMode) }
    var lang by remember(refresh) { mutableStateOf(prefs.lang) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val strings = when (lang) {
        Lang.RU -> RU
        Lang.EN -> EN
        Lang.SYSTEM -> if (systemLanguageIsRussian()) RU else EN
    }

    GuardTheme(dark) {
        CompositionLocalProvider(LocalStrings provides strings) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    prefs = prefs,
                    refresh = refresh,
                    onRefresh = { refresh++ },
                    themeMode = themeMode,
                    onTheme = { themeMode = it; prefs.themeMode = it },
                    lang = lang,
                    onLang = { lang = it; prefs.lang = it },
                    onOpenLog = { screen = Screen.LOG }
                )
                Screen.LOG -> LogScreen(
                    prefs = prefs,
                    onBack = { screen = Screen.HOME }
                )
            }
        }
    }
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    val cfg = LocalConfiguration.current
    return (cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}

private fun systemLanguageIsRussian(): Boolean =
    Locale.getDefault().language.equals("ru", ignoreCase = true)

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
    onOpenLog: () -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current

    var filterEnabled by remember(refresh) { mutableStateOf(prefs.filterEnabled) }
    var strictMode by remember(refresh) { mutableStateOf(prefs.strictMode) }
    var silenceCalls by remember(refresh) { mutableStateOf(prefs.silenceUnknownCalls) }
    var storeText by remember(refresh) { mutableStateOf(prefs.storeLogText) }
    var allowed by remember(refresh) { mutableStateOf(prefs.allowedApps) }
    var blockWords by remember(refresh) { mutableStateOf(prefs.customBlockWords) }
    var allowWords by remember(refresh) { mutableStateOf(prefs.customAllowWords) }

    val listenerOn = remember(refresh) { GuardNotificationListener.isEnabled(context) }
    val screeningOn = remember(refresh) { isCallScreener(context) }
    val contactsOn = remember(refresh) { ContactsRepo.hasPermission(context) }

    var showPicker by remember { mutableStateOf(false) }

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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ActionTile(
                    title = s.openLog,
                    subtitle = s.openLogHint,
                    onClick = onOpenLog
                )
            }

            item {
                Section(s.accessTitle) {
                    StatusRow(
                        s.accessNotifications,
                        listenerOn,
                        if (listenerOn) s.accessNotificationsOn else s.accessNotificationsOff,
                        s.actionOpenSettings
                    ) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    StatusRow(
                        s.accessCalls,
                        screeningOn,
                        if (screeningOn) s.accessCallsOn else s.accessCallsOff,
                        s.actionAssign
                    ) {
                        requestScreeningRole(context)?.let { roleLauncher.launch(it) }
                    }
                    StatusRow(
                        s.accessContacts,
                        contactsOn,
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
                    Divider()
                    SwitchRow(s.strictMode, s.strictModeHint, strictMode) {
                        strictMode = it; prefs.strictMode = it
                    }
                    Divider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
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
                Section(s.stopWordsTitle) {
                    Hint(s.stopWordsHint)
                    WordEditor(
                        placeholder = s.stopWordsPlaceholder,
                        words = blockWords,
                        onAdd = { prefs.addBlockWord(it); blockWords = prefs.customBlockWords },
                        onRemove = { prefs.removeBlockWord(it); blockWords = prefs.customBlockWords }
                    )
                }
            }

            item {
                Section(s.allowWordsTitle) {
                    Hint(s.allowWordsHint)
                    WordEditor(
                        placeholder = s.allowWordsPlaceholder,
                        words = allowWords,
                        onAdd = { prefs.addAllowWord(it); allowWords = prefs.customAllowWords },
                        onRemove = { prefs.removeAllowWord(it); allowWords = prefs.customAllowWords }
                    )
                }
            }

            item {
                Section(s.callsTitle) {
                    SwitchRow(s.silenceUnknown, s.silenceUnknownHint, silenceCalls) {
                        silenceCalls = it; prefs.silenceUnknownCalls = it
                    }
                    if (silenceCalls && !screeningOn) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.callsWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Section(s.appearanceTitle) {
                    Text(s.theme, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        options = listOf(s.themeSystem, s.themeLight, s.themeDark),
                        selectedIndex = themeMode.ordinal,
                        onSelect = { onTheme(ThemeMode.entries[it]) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(s.language, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SegmentedRow(
                        options = listOf(s.langSystem, s.langRu, s.langEn),
                        selectedIndex = lang.ordinal,
                        onSelect = { onLang(Lang.entries[it]) }
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
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text(s.tabNotifications) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text(s.tabCalls) })
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) {
                    if (notifications.isEmpty()) {
                        item { EmptyNote(s.emptyNotifications) }
                    } else {
                        items(notifications) { e ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
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
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            c.number.ifBlank { "—" },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "${c.timeText()} · " +
                                                if (c.silenced) s.silenced else s.allowedCall,
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
}

/* ------------------------------- Общие элементы ----------------------------- */

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
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
private fun ActionTile(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (ok) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error
                )
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
        if (!ok) {
            Spacer(Modifier.width(8.dp))
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
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WordEditor(
    placeholder: String,
    words: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val s = LocalStrings.current
    var input by remember { mutableStateOf("") }

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

    Spacer(Modifier.height(10.dp))

    if (words.isEmpty()) {
        Text(
            s.emptyList,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        words.sorted().forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
        Spacer(Modifier.height(8.dp))
        Text(
            s.wordMatchHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                        items(filtered) { (pkg, label) ->
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
                                if (pkg in selected) {
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
