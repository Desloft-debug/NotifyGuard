package com.guard.notifyguard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Карточка номера из журнала звонков.

@Composable
internal fun NumberDialog(number: String, onDismiss: () -> Unit) {
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
    // ключи те же, что кладёт NumberLookup.analyze
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
