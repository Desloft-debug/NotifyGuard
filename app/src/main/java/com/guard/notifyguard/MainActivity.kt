package com.guard.notifyguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.util.Locale

// Точка входа и общий каркас: вкладки, тема, язык, первый запуск.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Tab(val icon: ImageVector) {
    PROTECT(Icons.Filled.Lock),
    DICT(Icons.Filled.Search),
    LOG(Icons.Filled.List),
    HELP(Icons.Filled.Info)
}

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    // Из системных настроек человек возвращается уже с другими разрешениями,
    // поэтому на каждый onResume перечитываем всё, что зависит от системы.
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
        CompositionLocalProvider(LocalStrings provides strings, LocalLang provides lang) {
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
                // едем в ту сторону, в которую переключили вкладку
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
