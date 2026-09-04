package com.guard.notifyguard

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Вкладка «Справка»: пошаговая настройка и форма обратной связи.

@Composable
internal fun HelpScreen(
    prefs: Prefs,
    refresh: Int,
    onRefresh: () -> Unit,
    notify: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current

    // инструкцию можно свернуть навсегда, но вернуть тоже можно
    var hidden by remember(refresh) { mutableStateOf(prefs.onboardingDone) }
    val access = rememberAccess(refresh)
    val listenerOn = access.listener
    val screeningOn = access.screening
    val contactsOn = access.contacts
    val allDone = access.allGranted

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
                    openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
