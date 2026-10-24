package com.cleanyourtv.mobile.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cleanyourtv.mobile.R
import com.cleanyourtv.mobile.adb.DiscoveredEndpoint
import com.cleanyourtv.mobile.data.BackupRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConnectScreen(
    state: UiState,
    onConnect: (String, String) -> Unit,
    onPair: (String, String, String, String) -> Unit,
    onDiscover: () -> Unit,
    onRestore: (String) -> Unit,
) {
    var host by rememberSaveable(state.host) { mutableStateOf(state.host) }
    var port by rememberSaveable(state.port) { mutableStateOf(state.port) }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var showPair by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenTitle(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.connect_subtitle),
            )
        }

        item {
            SectionCard(title = stringResource(R.string.connect_card)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.tv_ip)) },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryAction(
                    text = stringResource(R.string.connect),
                    onClick = { onConnect(host, port) },
                    enabled = !state.busy && host.isNotBlank(),
                )
                BoxedHint(stringResource(R.string.connect_allow_hint))
            }
        }

        item {
            SectionCard(title = stringResource(R.string.discover_card)) {
                SecondaryAction(
                    text = stringResource(if (state.mdnsRunning) R.string.discovering else R.string.discover),
                    onClick = onDiscover,
                    enabled = !state.busy,
                )
                if (state.discovered.isEmpty() && state.discoveredPairing.isEmpty() && state.mdnsRunning) {
                    Text(
                        stringResource(R.string.discover_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.discovered.isNotEmpty() || state.discoveredPairing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.discovered),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    for (endpoint in state.discovered) {
                        EndpointRow(endpoint) {
                            host = endpoint.host
                            port = endpoint.port.toString()
                        }
                    }
                    for (endpoint in state.discoveredPairing) {
                        EndpointRow(endpoint, pairing = true) {
                            host = endpoint.host
                            pairPort = endpoint.port.toString()
                            showPair = true
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                TextButton(onClick = { showPair = !showPair }) {
                    Text(stringResource(if (showPair) R.string.hide_pairing else R.string.show_pairing))
                }
                if (showPair) {
                    Text(
                        stringResource(R.string.pair_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pairPort,
                        onValueChange = { pairPort = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.pair_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pairCode,
                        onValueChange = { pairCode = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.pair_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryAction(
                        text = stringResource(R.string.pair_and_connect),
                        onClick = { onPair(host, pairPort, pairCode, port) },
                        enabled = !state.busy && host.isNotBlank() && pairPort.isNotBlank() && pairCode.length == 6,
                    )
                }
            }
        }

        item {
            SectionCard {
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(stringResource(if (showHelp) R.string.hide_help else R.string.show_help))
                }
                if (showHelp) {
                    Text(
                        stringResource(R.string.adb_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.saved_backups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.backups.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_backups),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.backups, key = { it.id }) { record ->
                BackupRow(record, onRestore = { onRestore(record.id) })
            }
        }
    }
}

@Composable
private fun EndpointRow(endpoint: DiscoveredEndpoint, pairing: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(endpoint.name.ifBlank { endpoint.host }, fontWeight = FontWeight.Medium)
            Text(
                "${endpoint.host}:${endpoint.port} · " + if (pairing) {
                    stringResource(R.string.pairing_endpoint)
                } else {
                    stringResource(R.string.connect_endpoint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupRow(record: BackupRecord, onRestore: () -> Unit) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.device.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    record.listTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatDate(record.createdAt)} · ${record.host} · " +
                        stringResource(R.string.disabled_short, record.disabledCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.restore))
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
