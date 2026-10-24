package com.cleanyourtv.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cleanyourtv.mobile.R

@Composable
fun RunScreen(state: UiState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.scrollToItem(state.logs.lastIndex)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = if (state.runMode == RunMode.RESTORE) {
                stringResource(R.string.restore_title)
            } else {
                stringResource(R.string.run_title)
            },
            subtitle = state.runStep,
        )

        val fraction = if (state.runTotal > 0) {
            (state.runProgress.toFloat() / state.runTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (state.runTotal > 0) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.run_progress, state.runProgress, state.runTotal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        BoxedHint(
            if (state.runMode == RunMode.RESTORE) {
                stringResource(R.string.restore_keep_open)
            } else {
                stringResource(R.string.run_keep_open)
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.logs) { _, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "›",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
