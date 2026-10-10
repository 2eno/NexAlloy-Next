package io.github.nexalloy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.nexalloy.core.R
import io.github.nexalloy.ui.MainActivity
import io.github.nexalloy.ui.ManagerState
import io.github.nexalloy.ui.ManagerViewModel
import io.github.nexalloy.ui.navigateTopLevel

@Composable
fun HomeScreen(viewModel: ManagerViewModel, navController: NavHostController, outerPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state, outerPadding,
        onCheckUpdates = { viewModel.checkUpdates(userInitiated = true) },
        onUpdateAll = viewModel::updateAll,
        onRequestScope = { viewModel.requestMissingScope() },
        onOpenPatches = { navController.navigateTopLevel(MainActivity.DESTINATION_PATCHES) },
        onOpenApp = { navController.navigate("${MainActivity.DESTINATION_APP}/$it") },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: ManagerState,
    outerPadding: PaddingValues,
    onCheckUpdates: () -> Unit,
    onUpdateAll: () -> Unit,
    onRequestScope: () -> Unit,
    onOpenPatches: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var changelog by remember { mutableStateOf<Pair<String, String>?>(null) }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCheckUpdates, enabled = !state.checkingUpdates) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.check_for_update_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                StatusCard(
                    active = state.isActive,
                    title = stringResource(if (state.isActive) R.string.module_active_title else R.string.module_inactive_title),
                    text = if (state.isActive) state.framework.orEmpty() else stringResource(R.string.module_inactive_summary),
                )
            }

            state.appUpdate?.let { release ->
                item {
                    ActionCard(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.app_update_title, release.tag),
                        text = stringResource(R.string.app_update_text),
                        primary = stringResource(R.string.action_download) to { openUrl(context, release.apkUrl ?: release.pageUrl) },
                        secondary = stringResource(R.string.action_changelog) to { changelog = release.tag to release.notes },
                    )
                }
            }

            val updates = state.sourcesWithUpdates
            if (updates.isNotEmpty() && state.isActive) {
                item {
                    ActionCard(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.patch_updates_title),
                        text = updates.joinToString { "${it.name} ${state.releases[it.id]?.getOrNull()?.version}" },
                        primary = stringResource(R.string.action_update_all) to onUpdateAll,
                        secondary = stringResource(R.string.nav_patches) to onOpenPatches,
                    )
                }
            }

            if (state.missingScope.isNotEmpty()) {
                item {
                    ActionCard(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.scope_missing_title),
                        text = stringResource(R.string.scope_missing_text, state.missingScope.mapNotNull { state.apps[it] }.joinToString()),
                        primary = stringResource(R.string.action_request) to onRequestScope,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.apps_title)) }

            val apps = state.apps.entries.sortedBy { it.value.lowercase() }
            if (apps.isEmpty()) {
                item {
                    ActionCard(
                        title = stringResource(R.string.no_bundles_title),
                        text = stringResource(R.string.no_bundles_text),
                        primary = stringResource(R.string.action_browse_sources) to onOpenPatches,
                    )
                }
            }
            items(apps, key = { it.key }) { (packageName, appName) ->
                val bundles = state.bundles.filter { it.enabled && packageName in it.apps }
                AppCard(
                    appName = appName,
                    packageName = packageName,
                    recommendedVersion = bundles.firstNotNullOfOrNull { it.recommendedVersion(packageName) },
                    bundles = bundles.joinToString { it.name },
                    onClick = { onOpenApp(packageName) },
                )
            }
        }
    }

    changelog?.let { (title, notes) ->
        AlertDialog(
            onDismissRequest = { changelog = null },
            confirmButton = { TextButton(onClick = { changelog = null }) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(title) },
            text = { Text(notes.ifBlank { "-" }) },
        )
    }
}

@Composable
private fun StatusCard(active: Boolean, title: String, text: String) {
    val colors = if (active) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (active) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    text: String,
    icon: ImageVector? = null,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = LocalContentColor.current.takeIf {
                        containerColor != MaterialTheme.colorScheme.surfaceContainerHigh
                    } ?: MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (primary != null || secondary != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    secondary?.let { (label, action) -> OutlinedButton(onClick = action) { Text(label) } }
                    primary?.let { (label, action) -> Button(onClick = action) { Text(label) } }
                }
            }
        }
    }
}
