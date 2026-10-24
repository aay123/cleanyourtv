package com.cleanyourtv.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cleanyourtv.mobile.R
import com.cleanyourtv.mobile.data.Analysis
import com.cleanyourtv.mobile.data.TvList

@Composable
fun AnalyzeScreen(
    state: UiState,
    onBack: () -> Unit,
    onSelectList: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPickApk: (android.net.Uri) -> Unit,
    onClearApk: () -> Unit,
    onDns: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(state.selectedList == null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showSkipped by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.analysis) {
        if (state.analysis != null) choosing = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickApk(uri)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = state.device?.label ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DeviceCard(state)
            }

            if (choosing || state.selectedList == null) {
                item {
                    SectionCard(title = stringResource(R.string.choose_list)) {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = onSearch,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(R.string.search_lists)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val suggestions = state.suggestions
                        if (suggestions.isNotEmpty()) {
                            Text(
                                stringResource(R.string.suggested_for_tv),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            for (list in suggestions) {
                                ListRow(list, selected = list.id == state.selectedList?.id) {
                                    showAll = false
                                    onSelectList(list.id)
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.no_match),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.search.isBlank() && !showAll) {
                            TextButton(onClick = { showAll = true }) {
                                Text(stringResource(R.string.browse_all, state.lists.size))
                            }
                        }
                        if (showAll || state.search.isNotBlank()) {
                            val shown = remember(state.search, state.lists, suggestions) {
                                val suggestionIds = suggestions.map { it.id }.toSet()
                                val query = state.search.trim().lowercase()
                                state.lists
                                    .asSequence()
                                    .filter { it.id !in suggestionIds }
                                    .filter {
                                        query.isEmpty() ||
                                            it.title.lowercase().contains(query) ||
                                            it.id.lowercase().contains(query) ||
                                            it.os.lowercase().contains(query)
                                    }
                                    .take(80)
                                    .toList()
                            }
                            if (shown.isNotEmpty()) {
                                HorizontalDivider()
                                for (list in shown) {
                                    ListRow(list, selected = list.id == state.selectedList?.id) {
                                        showAll = false
                                        onSelectList(list.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.selectedList?.let { list ->
                item {
                    SelectedListCard(
                        list = list,
                        analysis = state.analysis,
                        onChooseAnother = { choosing = true },
                        showSkipped = showSkipped,
                        onToggleSkipped = { showSkipped = !showSkipped },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.launcher_card)) {
                    Text(
                        stringResource(R.string.launcher_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.launcherName == null) {
                        SecondaryAction(
                            text = stringResource(R.string.choose_apk),
                            onClick = { picker.launch(arrayOf("*/*")) },
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.launcherName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    state.launcherPackage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onClearApk) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
                            }
                        }
                    }
                    BoxedHint(stringResource(R.string.launcher_keep_hint))
                }
            }

            item {
                SectionCard(title = stringResource(R.string.dns_card)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dns_title), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.dns_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.applyDns, onCheckedChange = onDns)
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val analysis = state.analysis
                if (analysis != null) {
                    Text(
                        stringResource(
                            R.string.plan_summary,
                            analysis.plan.effective.size,
                            analysis.plan.skippedCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryAction(
                    text = stringResource(R.string.start_clean),
                    onClick = { showConfirm = true },
                    enabled = analysis != null && analysis.plan.effective.isNotEmpty() && !state.busy,
                )
            }
        }
    }

    if (showConfirm && state.analysis != null) {
        ConfirmDialog(
            analysis = state.analysis,
            launcherName = state.launcherName,
            applyDns = state.applyDns,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onStart()
            },
        )
    }
}

@Composable
private fun DeviceCard(state: UiState) {
    val device = state.device ?: return
    SectionCard(title = stringResource(R.string.device_card)) {
        LabeledValue(stringResource(R.string.model), device.model.ifBlank { device.device })
        LabeledValue(stringResource(R.string.brand), device.brand.ifBlank { device.manufacturer })
        LabeledValue(
            stringResource(R.string.android),
            listOf(device.androidVersion, "SDK ${device.sdk}").filter { it.isNotBlank() }.joinToString(" · "),
        )
        if (device.isFireTv) {
            Text(
                stringResource(R.string.fire_tv_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListRow(list: TvList, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(list.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                listOf(list.os, stringResource(R.string.packages_short, list.packageCount))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedListCard(
    list: TvList,
    analysis: Analysis?,
    onChooseAnother: () -> Unit,
    showSkipped: Boolean,
    onToggleSkipped: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.selected_list)) {
        Text(list.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            listOf(list.os, stringResource(R.string.packages_short, list.packageCount))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onChooseAnother) {
            Text(stringResource(R.string.change_list))
        }
        if (analysis == null) return@SectionCard

        LabeledValue(stringResource(R.string.will_disable), analysis.plan.effective.size.toString())
        LabeledValue(stringResource(R.string.will_skip), analysis.plan.skippedCount.toString())

        if (analysis.plan.findings.isNotEmpty()) {
            Text(
                stringResource(R.string.safety_check),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            for (finding in analysis.plan.findings) {
                FindingRow(finding)
            }
        }

        if (analysis.plan.skipped.isNotEmpty()) {
            TextButton(onClick = onToggleSkipped) {
                Text(stringResource(if (showSkipped) R.string.hide_skipped else R.string.show_skipped))
            }
            if (showSkipped) {
                for ((name, reason) in analysis.plan.skipped) {
                    SkippedRow(name, reason)
                }
            }
        }

        if (analysis.plan.currentHome != null) {
            BoxedHint(stringResource(R.string.current_home, analysis.plan.currentHome))
        }
        if (analysis.plan.isCvteBoard) {
            BoxedHint(stringResource(R.string.cvte_hint))
        }
    }
}

@Composable
private fun SkippedRow(name: String, reason: String) {
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

@Composable
private fun ConfirmDialog(
    analysis: Analysis,
    launcherName: String?,
    applyDns: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val plan = analysis.plan
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.confirm_body))
                LabeledValue(stringResource(R.string.confirm_packages), plan.effective.size.toString())
                LabeledValue(stringResource(R.string.confirm_skipped), plan.skippedCount.toString())
                LabeledValue(
                    stringResource(R.string.confirm_launcher),
                    launcherName ?: stringResource(R.string.confirm_launcher_none),
                )
                LabeledValue(
                    stringResource(R.string.confirm_dns),
                    stringResource(if (applyDns) R.string.yes else R.string.no),
                )
                Text(
                    stringResource(R.string.confirm_record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
