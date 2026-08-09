package com.guard.notifyguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GuardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var refresh by remember { mutableIntStateOf(0) }
    OnResume { refresh++ }

    var filterEnabled by remember(refresh) { mutableStateOf(prefs.filterEnabled) }
    var strictMode by remember(refresh) { mutableStateOf(prefs.strictMode) }
    var silenceCalls by remember(refresh) { mutableStateOf(prefs.silenceUnknownCalls) }
    var allowed by remember(refresh) { mutableStateOf(prefs.allowedApps) }

    val listenerOn by remember(refresh) {
        mutableStateOf(GuardNotificationListener.isEnabled(context))
    }
    val screeningOn by remember(refresh) { mutableStateOf(isCallScreener(context)) }
    val contactsOn by remember(refresh) { mutableStateOf(ContactsRepo.hasPermission(context)) }
    val log by remember(refresh) { mutableStateOf(BlockLog.read(context)) }

    var showAppPicker by remember { mutableStateOf(false) }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Тихие уведомления") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Section("Доступ") {
                    StatusRow(
                        title = "Чтение уведомлений",
                        ok = listenerOn,
                        hint = if (listenerOn) "Фильтр может снимать уведомления"
                        else "Без этого доступа фильтр не работает",
                        action = "Открыть настройки"
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusRow(
                        title = "Приложение для проверки звонков",
                        ok = screeningOn,
                        hint = if (screeningOn) "Можно приглушать незнакомые номера"
                        else "Нужно, чтобы приглушать звонки",
                        action = "Назначить"
                    ) {
                        requestScreeningRole(context)?.let { roleLauncher.launch(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusRow(
                        title = "Доступ к контактам",
                        ok = contactsOn,
                        hint = if (contactsOn) "Знакомые номера звонят как обычно"
                        else "Без контактов все номера считаются знакомыми",
                        action = "Разрешить"
                    ) {
                        contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }

            item {
                Section("Уведомления") {
                    SwitchRow(
                        title = "Скрывать рекламу",
                        subtitle = "Коды подтверждения, операции по счёту и экстренные сообщения остаются",
                        checked = filterEnabled
                    ) {
                        filterEnabled = it
                        prefs.filterEnabled = it
                    }
                    SwitchRow(
                        title = "Строгий режим",
                        subtitle = "Показывать только белый список, коды и переводы. Остальное скрывать",
                        checked = strictMode
                    ) {
                        strictMode = it
                        prefs.strictMode = it
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Белый список: ${allowed.size} приложений",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAppPicker = true }) {
                        Text("Выбрать приложения")
                    }
                }
            }

            item {
                Section("Звонки") {
                    SwitchRow(
                        title = "Приглушать незнакомые номера",
                        subtitle = "Звонок не из контактов проходит без звука и вибрации, но остаётся в журнале вызовов",
                        checked = silenceCalls
                    ) {
                        silenceCalls = it
                        prefs.silenceUnknownCalls = it
                    }
                    if (silenceCalls && !screeningOn) {
                        Text(
                            "Назначьте приложение для проверки звонков выше, иначе настройка ни на что не влияет",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Что было скрыто", style = MaterialTheme.typography.titleMedium)
                    if (log.isNotEmpty()) {
                        TextButton(onClick = { BlockLog.clear(context); refresh++ }) {
                            Text("Очистить")
                        }
                    }
                }
            }

            if (log.isEmpty()) {
                item {
                    Text(
                        "Пока ничего не скрыто. Здесь появятся уведомления, которые снял фильтр.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(log) { e ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                appLabel(context, e.pkg),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(e.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${e.timeText()} · ${e.reason}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            selected = allowed,
            onToggle = { pkg ->
                prefs.toggleAllowed(pkg)
                allowed = prefs.allowedApps
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
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
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (ok) "$title · включено" else "$title · выключено",
                style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) {
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun AppPickerDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Всегда показывать") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    Text("Загружаю список приложений…")
                } else {
                    val filtered = apps.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            it.pkg.contains(query, ignoreCase = true)
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.pkg in selected,
                                    onCheckedChange = { onToggle(app.pkg) }
                                )
                                Text(
                                    app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
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
