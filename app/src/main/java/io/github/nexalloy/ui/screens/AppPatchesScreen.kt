package io.github.nexalloy.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.nexalloy.core.R
import io.github.nexalloy.patchPreferenceKey
import io.github.nexalloy.ui.BundlePatches
import io.github.nexalloy.ui.ManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPatchesScreen(viewModel: ManagerViewModel, packageName: String, navController: NavHostController) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appName = state.apps[packageName] ?: packageName
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var result by remember { mutableStateOf<Result<List<BundlePatches>>?>(null) }
    // Patch toggles, keyed by preference key, so switches update without reloading.
    val enabled = remember { mutableStateMapOf<String, Boolean>() }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(packageName, state.isActive, state.bundles, reload) {
        val loaded = viewModel.patches(packageName)
        enabled.clear()
        loaded.getOrNull()?.forEach { group ->
            group.patches.forEach { (patch, on) -> enabled[patchPreferenceKey(group.bundle.id, patch.name)] = on }
        }
        result = loaded
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appName)
                        Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isInstalled(context, packageName)) {
                        IconButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.action_app_info)) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val groups = result?.getOrNull().orEmpty()
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_restore_defaults)) }, onClick = {
                            menu = false
                            viewModel.resetPatches(packageName, groups, defaults = true)
                            reload++
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_disable_all)) }, onClick = {
                            menu = false
                            viewModel.resetPatches(packageName, groups, defaults = false)
                            reload++
                        })
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val loaded = result
        when {
            loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            loaded.isFailure -> Text(
                stringResource(R.string.patches_load_failed, loaded.exceptionOrNull()?.message.orEmpty()),
                modifier = Modifier.padding(padding).padding(16.dp),
            )

            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    val recommended = state.bundles.filter { it.enabled }.firstNotNullOfOrNull { it.recommendedVersion(packageName) }
                    val installed = remember(packageName) { appLook(context, packageName).versionName }
                    ActionCard(
                        title = recommended?.let { stringResource(R.string.recommended_version, it) }
                            ?: stringResource(R.string.recommended_any_version),
                        text = installed?.let { stringResource(R.string.installed_version, it) }
                            ?: stringResource(R.string.app_not_installed_short),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.force_stop_to_apply_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                loaded.getOrThrow().forEach { group ->
                    item(key = group.bundle.id) { SectionHeader("${group.bundle.name} ${group.bundle.version}") }
                    items(group.patches, key = { "${group.bundle.id}/${it.first.name}" }) { (patch, _) ->
                        val key = patchPreferenceKey(group.bundle.id, patch.name)
                        val checked = enabled[key] ?: patch.use
                        fun toggle(value: Boolean) {
                            enabled[key] = value
                            viewModel.setPatchEnabled(packageName, group.bundle, patch, value)
                        }
                        ListItem(
                            headlineContent = { Text(patch.name) },
                            supportingContent = patch.description.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                            trailingContent = { Switch(checked = checked, onCheckedChange = ::toggle) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { toggle(!checked) },
                        )
                    }
                }
            }
        }
    }
}
