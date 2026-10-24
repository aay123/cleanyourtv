package com.cleanyourtv.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanyourtv.mobile.ui.AnalyzeScreen
import com.cleanyourtv.mobile.ui.AppViewModel
import com.cleanyourtv.mobile.ui.ConnectScreen
import com.cleanyourtv.mobile.ui.DoneScreen
import com.cleanyourtv.mobile.ui.RunScreen
import com.cleanyourtv.mobile.ui.Screen
import com.cleanyourtv.mobile.ui.UiState
import com.cleanyourtv.mobile.ui.theme.CleanYourTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanYourTvTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AppRoot(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun AppRoot(state: UiState, viewModel: AppViewModel) {
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message)
        viewModel.consumeToast()
    }

    BackHandler(enabled = state.screen == Screen.RUN) { }
    BackHandler(enabled = state.screen == Screen.ANALYZE) { viewModel.backToConnect() }
    BackHandler(enabled = state.screen == Screen.DONE) { viewModel.backToConnect() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.CONNECT -> ConnectScreen(
                    state = state,
                    onConnect = viewModel::connect,
                    onPair = viewModel::pair,
                    onDiscover = viewModel::discover,
                    onRestore = viewModel::restore,
                )

                Screen.ANALYZE -> AnalyzeScreen(
                    state = state,
                    onBack = viewModel::backToConnect,
                    onSelectList = viewModel::selectList,
                    onSearch = viewModel::setSearch,
                    onPickApk = viewModel::setLauncherApk,
                    onClearApk = viewModel::clearLauncherApk,
                    onDns = viewModel::setApplyDns,
                    onStart = viewModel::startClean,
                )

                Screen.RUN -> RunScreen(state = state)

                Screen.DONE -> DoneScreen(
                    state = state,
                    onExport = viewModel::exportBackup,
                    onRestore = viewModel::restore,
                    onReboot = viewModel::rebootTv,
                    onNewTv = viewModel::backToConnect,
                )
            }
        }
    }

    if (state.busy && state.screen != Screen.RUN) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.working)) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(state.busyText.ifBlank { stringResource(R.string.please_wait) })
                }
            },
            confirmButton = { },
        )
    }

    val error = state.error
    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}
