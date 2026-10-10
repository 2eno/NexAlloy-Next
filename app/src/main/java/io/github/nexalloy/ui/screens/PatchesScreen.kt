package io.github.nexalloy.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.core.R
import io.github.nexalloy.manager.PatchSource
import io.github.nexalloy.ui.ManagerState
import io.github.nexalloy.ui.ManagerViewModel

@Composable
fun PatchesScreen(viewModel: ManagerViewModel, outerPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PatchesContent(
        state, outerPadding,
        onCheckUpdates = { viewModel.checkUpdates(userInitiated = true) },
        onImport = viewModel::importBundle,
        onInstall = viewModel::install,
        onRemoveSource = viewModel::removeSource,
        onAddSource = viewModel::addSource,
        onSetEnabled = viewModel::setEnabled,
        onUninstall = viewModel::uninstall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchesContent(
    state: ManagerState,
    outerPadding: PaddingValues,
    onCheckUpdates: () -> Unit,
    onImport: (Uri) -> Unit,
    onInstall: (PatchSource) -> Unit,
    onRemoveSource: (PatchSource) -> Unit,
    onAddSource: (String, String, Boolean) -> Unit,
    onSetEnabled: (InstalledBundle, Boolean) -> Unit,
    onUninstall: (InstalledBundle) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    var addSource by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_patches)) },
                actions = {
                    IconButton(onClick = onCheckUpdates, enabled = !state.checkingUpdates) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.check_for_update_title))
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_bundle_title)) },
                            enabled = state.isActive,
                            onClick = { menu = false; importer.launch(arrayOf("*/*")) },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addSource = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_source_title)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!state.isActive) {
                item {
                    ActionCard(
                        title = stringResource(R.string.module_inactive_title),
                        text = stringResource(R.string.module_inactive_summary),
                        icon = Icons.Filled.Warning,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.sources_category)) }
            items(state.sources, key = { it.id }) { source ->
                SourceCard(
                    source, state,
                    onInstall = { onInstall(source) },
                    onChangelog = { release -> changelog = "${source.name} ${release.first}" to release.second },
                    onRemove = { confirm = source.name to { onRemoveSource(source) } },
                )
            }
            item { SectionHeader(stringResource(R.string.installed_bundles_category)) }
            if (state.bundles.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_bundles_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.bundles.sortedBy { it.name.lowercase() }, key = { it.id }) { bundle ->
                BundleCard(
                    bundle,
                    onEnabled = { onSetEnabled(bundle, it) },
                    onUninstall = { confirm = bundle.name to { onUninstall(bundle) } },
                )
            }
            item {
                Text(
                    stringResource(R.string.patch_sources_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (addSource) AddSourceDialog(onDismiss = { addSource = false }) { name, url, prerelease ->
        addSource = false
        onAddSource(name, url, prerelease)
    }
    changelog?.let { (title, notes) ->
        AlertDialog(
            onDismissRequest = { changelog = null },
            confirmButton = { TextButton(onClick = { changelog = null }) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(title) },
            text = { Text(notes.ifBlank { "-" }) },
        )
    }
    confirm?.let { (name, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.remove_title, name)) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun SourceCard(
    source: PatchSource,
    state: ManagerState,
    onInstall: () -> Unit,
    onChangelog: (Pair<String, String>) -> Unit,
    onRemove: () -> Unit,
) {
    val release = state.releases[source.id]
    val busy = source.id in state.busySources
    val status = when {
        busy -> stringResource(R.string.source_installing)
        release == null -> stringResource(R.string.source_checking)
        release.isFailure -> stringResource(R.string.source_error, release.exceptionOrNull()?.message.orEmpty())
        source.installedVersion == null -> stringResource(R.string.source_available, release.getOrThrow().version)
        state.hasUpdate(source) ->
            stringResource(R.string.source_update_available, source.installedVersion.orEmpty(), release.getOrThrow().version)

        else -> stringResource(R.string.source_up_to_date, source.installedVersion.orEmpty())
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (state.hasUpdate(source)) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        source.url, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove)) }
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
            ) {
                val latest = release?.getOrNull()
                if (latest != null && latest.changelog.isNotBlank()) {
                    TextButton(onClick = { onChangelog(latest.version to latest.changelog) }) {
                        Text(stringResource(R.string.action_changelog))
                    }
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    FilledTonalButton(onClick = onInstall, enabled = state.isActive) {
                        Text(
                            stringResource(
                                when {
                                    source.installedVersion == null -> R.string.action_install
                                    state.hasUpdate(source) -> R.string.action_update
                                    else -> R.string.action_reinstall
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleCard(bundle: InstalledBundle, onEnabled: (Boolean) -> Unit, onUninstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${bundle.name} ${bundle.version}", style = MaterialTheme.typography.titleMedium)
                    if (bundle.author.isNotEmpty()) {
                        Text(bundle.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = bundle.enabled, onCheckedChange = onEnabled)
            }
            if (bundle.description.isNotEmpty()) {
                Text(bundle.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                bundle.apps.values.sortedBy { it.lowercase() }.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onUninstall) { Text(stringResource(R.string.action_uninstall)) }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onAdd: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var prerelease by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.add_source_warning), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.source_url_hint)) }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.source_name_hint)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(prerelease, { prerelease = it })
                    Text(stringResource(R.string.source_prerelease))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url, prerelease) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
