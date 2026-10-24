package com.cleanyourtv.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cleanyourtv.mobile.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoneScreen(
    state: UiState,
    onExport: (android.net.Uri) -> Unit,
    onRestore: (String) -> Unit,
    onReboot: () -> Unit,
    onNewTv: () -> Unit,
) {
    var showReboot by rememberSaveable { mutableStateOf(false) }
    var showRestore by rememberSaveable { mutableStateOf(false) }
    var showFailed by rememberSaveable { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onExport(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SuccessIcon()
                Text(
                    text = if (state.runMode == RunMode.RESTORE) {
                        stringResource(R.string.restore_done_title)
                    } else {
                        stringResource(R.string.done_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.runMode == RunMode.RESTORE) {
                        stringResource(R.string.restore_done_body)
                    } else {
                        stringResource(R.string.done_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.runMode == RunMode.RESTORE) {
            item {
                val summary = state.restoreSummary
                SectionCard(title = stringResource(R.string.restore_summary)) {
                    if (summary == null) {
                        Text(stringResource(R.string.restore_summary_missing))
                    } else {
                        LabeledValue(stringResource(R.string.restore_enabled), summary.enabled.toString())
                        LabeledValue(stringResource(R.string.restore_already), summary.alreadyEnabled.toString())
                        LabeledValue(stringResource(R.string.restore_failed), summary.failed.size.toString())
                        LabeledValue(
                            stringResource(R.string.restore_home),
                            stringResource(if (summary.homeRestored) R.string.yes else R.string.no),
                        )
                        if (summary.failed.isNotEmpty()) {
                            TextButton(onClick = { showFailed = !showFailed }) {
                                Text(stringResource(if (showFailed) R.string.hide_failed else R.string.show_failed))
                            }
                            if (showFailed) {
                                for ((name, reason) in summary.failed) FailedRow(name, reason)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                val record = state.record
                SectionCard(title = stringResource(R.string.done_summary)) {
                    if (record == null) {
                        Text(stringResource(R.string.done_no_record))
                    } else {
                        LabeledValue(stringResource(R.string.done_tv), record.device.label)
                        LabeledValue(stringResource(R.string.done_list), record.listTitle)
                        LabeledValue(stringResource(R.string.done_disabled), record.disabledCount.toString())
                        LabeledValue(
                            stringResource(R.string.done_skipped),
                            state.analysis?.plan?.skippedCount?.toString() ?: "—",
                        )
                        LabeledValue(stringResource(R.string.done_failed), record.failedCount.toString())
                        LabeledValue(stringResource(R.string.done_recorded), formatDate(record.createdAt))
                        if (!record.finished) {
                            BoxedHint(stringResource(R.string.done_unfinished))
                        }
                        if (record.failed.isNotEmpty()) {
                            TextButton(onClick = { showFailed = !showFailed }) {
                                Text(stringResource(if (showFailed) R.string.hide_failed else R.string.show_failed))
                            }
                            if (showFailed) {
                                for ((name, reason) in record.failed) FailedRow(name, reason)
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.done_actions)) {
                    PrimaryAction(
                        text = stringResource(R.string.export_backup),
                        onClick = {
                            exporter.launch("clean-your-tv-backup-${System.currentTimeMillis()}.zip")
                        },
                        enabled = state.record != null && !state.busy,
                    )
                    SecondaryAction(
                        text = stringResource(R.string.restore_button),
                        onClick = { showRestore = true },
                        enabled = state.record != null && !state.busy,
                    )
                    SecondaryAction(
                        text = stringResource(R.string.reboot_tv),
                        onClick = { showReboot = true },
                        enabled = !state.busy,
                    )
                    SecondaryAction(
                        text = stringResource(R.string.new_tv),
                        onClick = onNewTv,
                    )
                }
            }
        }

        if (state.runMode == RunMode.RESTORE) {
            item {
                PrimaryAction(text = stringResource(R.string.new_tv), onClick = onNewTv)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.close_adb_title)) {
                Text(
                    stringResource(R.string.close_adb_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            title = { Text(stringResource(R.string.reboot_tv)) },
            text = { Text(stringResource(R.string.reboot_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showReboot = false
                    onReboot()
                }) { Text(stringResource(R.string.reboot_now)) }
            },
            dismissButton = {
                TextButton(onClick = { showReboot = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showRestore && state.record != null) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestore = false
                    state.record?.let { onRestore(it.id) }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun FailedRow(name: String, reason: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(
            reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
