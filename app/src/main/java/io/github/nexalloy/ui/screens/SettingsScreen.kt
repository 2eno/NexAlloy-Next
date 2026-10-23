package io.github.nexalloy.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nexalloy.core.BuildConfig
import io.github.nexalloy.core.R
import io.github.nexalloy.ui.ManagerViewModel

private val licenses = listOf(
    Triple("Morphe Patcher", "GNU General Public License v3.0", "https://github.com/MorpheApp/morphe-patcher"),
    Triple("DexKit", "LGPL-3.0, LuckyPray", "https://github.com/LuckyPray/DexKit"),
    Triple("smali / dexlib2", "BSD 3-Clause, Google", "https://github.com/google/smali"),
    Triple("Kotlin", "Apache License 2.0, JetBrains", "https://kotlinlang.org"),
    Triple("AndroidX, Jetpack Compose", "Apache License 2.0, Google", "https://developer.android.com/jetpack/androidx"),
    Triple("libxposed", "Apache License 2.0", "https://github.com/libxposed"),
    Triple("LSPosed", "GNU General Public License v3.0", "https://github.com/LSPosed/LSPosed"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ManagerViewModel, outerPadding: PaddingValues) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var notifyApp by remember { mutableStateOf(settings.notifyAppUpdates) }
    var notifyPatches by remember { mutableStateOf(settings.notifyPatchUpdates) }
    var guardDefault by remember { mutableStateOf(settings.guardByDefault) }
    var iconHidden by remember { mutableStateOf(viewModel.launcherIconHidden) }
    var showLicenses by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(bottom = outerPadding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item { SectionHeader(stringResource(R.string.settings_updates)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_notify_app)) },
                    supportingContent = { Text(stringResource(R.string.setting_notify_app_summary)) },
                    trailingContent = {
                        Switch(notifyApp, { notifyApp = it; settings.notifyAppUpdates = it })
                    },
                    colors = transparent,
                    modifier = Modifier.clickable { notifyApp = !notifyApp; settings.notifyAppUpdates = notifyApp },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_notify_patches)) },
                    supportingContent = { Text(stringResource(R.string.setting_notify_patches_summary)) },
                    trailingContent = {
                        Switch(notifyPatches, { notifyPatches = it; settings.notifyPatchUpdates = it })
                    },
                    colors = transparent,
                    modifier = Modifier.clickable { notifyPatches = !notifyPatches; settings.notifyPatchUpdates = notifyPatches },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.check_for_update_title)) },
                    colors = transparent,
                    modifier = Modifier.clickable { viewModel.checkUpdates(userInitiated = true) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_guard_default)) },
                    supportingContent = { Text(stringResource(R.string.setting_guard_default_summary)) },
                    trailingContent = { Switch(guardDefault, { guardDefault = it; settings.guardByDefault = it }) },
                    colors = transparent,
                    modifier = Modifier.clickable { guardDefault = !guardDefault; settings.guardByDefault = guardDefault },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_general)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hide_icon_title)) },
                    supportingContent = { Text(stringResource(R.string.hide_icon_summary)) },
                    trailingContent = { Switch(iconHidden, { iconHidden = it; viewModel.launcherIconHidden = it }) },
                    colors = transparent,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListItem(
                    headlineContent = { Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}") },
                    supportingContent = {
                        Text(
                            "${BuildConfig.COMMIT_HASH} ${BuildConfig.BUILD_TYPE} · " +
                                DateUtils.getRelativeTimeSpanString(BuildConfig.COMMIT_DATE * 1000)
                        )
                    },
                    colors = transparent,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.source_code)) },
                    supportingContent = { Text("github.com/${BuildConfig.UPDATE_REPOSITORY}") },
                    colors = transparent,
                    modifier = Modifier.clickable { openUrl(context, "https://github.com/${BuildConfig.UPDATE_REPOSITORY}") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.open_source_license)) },
                    colors = transparent,
                    modifier = Modifier.clickable { showLicenses = true },
                )
            }
            item {
                Text(
                    stringResource(R.string.slogan_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.open_source_license)) },
            text = {
                Column {
                    licenses.forEach { (name, license, url) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text(license) },
                            colors = transparent,
                            modifier = Modifier.clickable { openUrl(context, url) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
