package com.smsexpensetracker.ui.screens.settings

import android.content.Intent
import com.smsexpensetracker.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsexpensetracker.domain.value.SyncRange
import com.smsexpensetracker.ui.components.DemoDataBarrierDialog
import com.smsexpensetracker.ui.theme.ThemeMode
import java.time.Instant
import java.time.ZoneId

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit = {},
    onNavigateToBanks: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onNavigateToUnparsedSms: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    LaunchedEffect(state.csvMessage) {
        state.csvMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeCsvMessage()
        }
    }

    LaunchedEffect(state.demoMessage) {
        state.demoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeDemoMessage()
        }
    }

    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { export ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, export.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(send, "Export CSV"))
            }
            viewModel.consumePendingExport()
        }
    }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.size(24.dp))

        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        ThemeModeRow(
            icon = Icons.Outlined.BrightnessAuto,
            label = "System",
            selected = state.themeMode == ThemeMode.SYSTEM,
            onClick = { viewModel.onThemeModeChange(ThemeMode.SYSTEM) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.WbSunny,
            label = "Light",
            selected = state.themeMode == ThemeMode.LIGHT,
            onClick = { viewModel.onThemeModeChange(ThemeMode.LIGHT) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.Nightlight,
            label = "Dark",
            selected = state.themeMode == ThemeMode.DARK,
            onClick = { viewModel.onThemeModeChange(ThemeMode.DARK) }
        )
        ThemeModeRow(
            icon = Icons.Outlined.DarkMode,
            label = "AMOLED",
            subtitle = "Pure black background",
            selected = state.themeMode == ThemeMode.AMOLED,
            onClick = { viewModel.onThemeModeChange(ThemeMode.AMOLED) }
        )

        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = "Data",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCategories)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Categories",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToBanks)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Banks & Rules",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsActionRow(
            icon = Icons.Filled.Share,
            label = "Export CSV",
            onClick = { viewModel.exportCsv() }
        )
        SettingsActionRow(
            icon = Icons.Filled.FileOpen,
            label = "Import CSV",
            onClick = {
                importLauncher.launch(
                    arrayOf("text/csv", "text/comma-separated-values", "text/plain")
                )
            }
        )
        SettingsActionRow(
            icon = Icons.Filled.PlayArrow,
            label = "Load demo data",
            onClick = { viewModel.loadDemoData() }
        )
        if (state.demoDataLoaded) {
            SettingsActionRow(
                icon = Icons.Filled.DeleteForever,
                label = "Delete demo data",
                onClick = { viewModel.requestDeleteDemo() }
            )
        }
        SettingsActionRow(
            icon = Icons.Filled.SmsFailed,
            label = "Unparsed SMS",
            onClick = onNavigateToUnparsedSms
        )
        SettingsActionRow(
            icon = Icons.Filled.Description,
            label = "Logs",
            onClick = onNavigateToLogs
        )

        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = "Sync",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = state.lastSyncTime?.let { ts ->
                val dt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
                "Last sync: ${dt.dayOfMonth} ${dt.month.name.lowercase().take(3)} ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
            } ?: "Never synced",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.size(8.dp))

        val ranges = listOf(
            "1D" to SyncRange.LAST_1D,
            "1W" to SyncRange.LAST_1W,
            "2W" to SyncRange.LAST_2W,
            "1M" to SyncRange.LAST_1M,
            "3M" to SyncRange.LAST_3M,
            "All" to SyncRange.ALL
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranges.forEach { (label, range) ->
                FilterChip(
                    selected = state.selectedSyncRange == range,
                    onClick = { viewModel.onSyncRangeChange(range) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        SettingsActionRow(
            icon = Icons.Filled.Refresh,
            label = if (state.isSyncing) "Syncing…" else "Re-sync now",
            onClick = { if (!state.isSyncing) viewModel.resync() }
        )

        if (state.isSyncing) {
            Spacer(modifier = Modifier.size(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "SMS Expense Tracker",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (state.showDemoBarrier) {
            DemoDataBarrierDialog(
                onConfirmDelete = viewModel::confirmDeleteDemoData,
                onDismiss = viewModel::dismissDemoBarrier
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeModeRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
