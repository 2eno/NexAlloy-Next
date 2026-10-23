package io.github.nexalloy.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.libxposed.service.XposedService
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.Patch
import io.github.nexalloy.activity.SettingApplication
import io.github.nexalloy.core.BundleRegistry
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.core.ModuleStatus
import io.github.nexalloy.core.R
import io.github.nexalloy.manager.BundleManager
import io.github.nexalloy.manager.PatchSource
import io.github.nexalloy.manager.SourceClient
import io.github.nexalloy.manager.SourceRelease
import io.github.nexalloy.manager.SourceStore
import io.github.nexalloy.patchPreferenceKey
import io.github.nexalloy.updates.AppRelease
import io.github.nexalloy.guard.PlayUpdateGuard
import io.github.nexalloy.updates.AppSettings
import io.github.nexalloy.updates.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ManagerState(
    /** The Xposed service, null while the module is not active. */
    val service: XposedService? = null,
    /** Name and version of the framework running the module. */
    val framework: String? = null,
    val bundles: List<InstalledBundle> = emptyList(),
    val sources: List<PatchSource> = emptyList(),
    /** Latest release of each source, or the error fetching it. */
    val releases: Map<String, Result<SourceRelease>> = emptyMap(),
    /** Sources being installed. */
    val busySources: Set<String> = emptySet(),
    /** Installed apps patched by enabled bundles that are missing from the module scope. */
    val missingScope: List<String> = emptyList(),
    val appUpdate: AppRelease? = null,
    val checkingUpdates: Boolean = false,
) {
    val isActive get() = service != null

    /** Package name to display name of the apps patched by enabled bundles. */
    val apps: Map<String, String>
        get() = bundles.filter { it.enabled }.flatMap { it.apps.entries }
            .associate { it.key to it.value }

    fun hasUpdate(source: PatchSource): Boolean {
        val release = releases[source.id]?.getOrNull() ?: return false
        return source.installedVersion != null && source.installedVersion != release.version
    }

    val sourcesWithUpdates get() = sources.filter(::hasUpdate)
}

/** The patches of one bundle for one app, with whether each is enabled. */
class BundlePatches(val bundle: InstalledBundle, val patches: List<Pair<Patch, Boolean>>)

class ManagerViewModel(application: Application) : AndroidViewModel(application),
    SettingApplication.ServiceStateListener {

    private val context get() = getApplication<Application>()
    private val store = SourceStore(application)
    val settings = AppSettings(application)
    private val guard = PlayUpdateGuard(application, settings)

    private val _state = MutableStateFlow(ManagerState(sources = store.all()))
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for a snackbar. */
    val messages = _messages.asSharedFlow()

    private var ownScopeRequested = false

    init {
        SettingApplication.addServiceStateListener(this, true)
        checkUpdates()
    }

    override fun onCleared() {
        SettingApplication.removeServiceStateListener(this)
    }

    override fun onServiceStateChanged(service: XposedService?) {
        val active = service?.takeIf {
            runCatching { it.getRemotePreferences(BundleRegistry.PREFS_GROUP) }.isSuccess
        }
        val framework = active?.let { "${it.frameworkName} ${it.frameworkVersion}" }
            ?: ModuleStatus.frameworkName()
        _state.update { it.copy(service = active, framework = framework) }
        refresh()
        if (active != null) requestOwnScope(active)
    }

    private val manager get() = _state.value.service?.let { BundleManager(context, it) }

    private fun message(resource: Int, vararg args: Any?) {
        _messages.tryEmit(context.getString(resource, *args))
    }

    /** Re-reads bundles, sources and the scope. */
    fun refresh() {
        val manager = manager
        _state.update { state ->
            state.copy(
                sources = store.all(),
                bundles = manager?.let { runCatching { it.installed }.getOrNull() }.orEmpty(),
                missingScope = manager?.let { runCatching { it.missingScope() }.getOrNull() }.orEmpty(),
            )
        }
        if (manager != null) {
            // The background check and the Play update lock read these without the module's service.
            settings.recommendedVersions = _state.value.bundles.filter { it.enabled }
                .flatMap { bundle -> bundle.apps.keys.mapNotNull { pkg -> bundle.recommendedVersion(pkg)?.let { pkg to it } } }
                .toMap()
            viewModelScope.launch { runCatching { guard.reapply() } }
        }
    }

    /** Checks NexAlloy and all sources for updates. */
    fun checkUpdates(userInitiated: Boolean = false) {
        _state.update { it.copy(checkingUpdates = true) }
        viewModelScope.launch {
            val appUpdate = withContext(Dispatchers.IO) { runCatching { AppUpdateChecker.latest() } }
            val releases = withContext(Dispatchers.IO) {
                store.all().associate { it.id to runCatching { SourceClient.latestRelease(it) } }
            }
            _state.update {
                it.copy(
                    appUpdate = appUpdate.getOrNull()?.takeIf { release -> release.isNewer },
                    releases = releases,
                    checkingUpdates = false,
                )
            }
            if (userInitiated) {
                val state = _state.value
                when {
                    appUpdate.isFailure -> message(R.string.update_check_failed, appUpdate.exceptionOrNull()?.message)
                    state.appUpdate == null && state.sourcesWithUpdates.isEmpty() -> message(R.string.up_to_date)
                }
            }
        }
    }

    // region Sources

    fun install(source: PatchSource) {
        val manager = manager ?: return
        _state.update { it.copy(busySources = it.busySources + source.id) }
        val before = manager.installed
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val release = SourceClient.latestRelease(source)
                    val bundles = release.bundles.map { (_, url) ->
                        SourceClient.open(url).inputStream.use { manager.install(it, source.id) }
                    }
                    store.save(source.copy(installedVersion = release.version))
                    release to bundles
                }
            }
            _state.update { it.copy(busySources = it.busySources - source.id) }
            result.onSuccess { (release, bundles) ->
                _state.update { it.copy(releases = it.releases + (source.id to Result.success(release))) }
                message(R.string.bundles_installed, bundles.joinToString { "${it.name} ${it.version}" })
                requestMissingScope(bundles.flatMap { newApps(it, before) })
            }.onFailure { message(R.string.install_failed, it.message) }
            refresh()
        }
    }

    fun updateAll() = _state.value.sourcesWithUpdates.forEach(::install)

    fun addSource(name: String, url: String, prerelease: Boolean) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val source = PatchSource.create(name.trim().ifEmpty { trimmed.substringAfterLast('/') }, trimmed, prerelease)
        store.save(source)
        refresh()
        install(source)
    }

    fun removeSource(source: PatchSource) {
        store.remove(source.id)
        refresh()
    }

    fun importBundle(uri: Uri) {
        val manager = manager ?: return
        val before = manager.installed
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { manager.install(it, null) } }
            }
            result.onSuccess {
                message(R.string.bundles_installed, "${it.name} ${it.version}")
                requestMissingScope(newApps(it, before))
            }.onFailure { message(R.string.install_failed, it.message) }
            refresh()
        }
    }

    // endregion

    // region Bundles

    fun setEnabled(bundle: InstalledBundle, enabled: Boolean) {
        val manager = manager ?: return
        manager.setEnabled(bundle, enabled)
        if (enabled) requestMissingScope(bundle.apps.keys)
        refresh()
    }

    fun uninstall(bundle: InstalledBundle) {
        val manager = manager ?: return
        manager.uninstall(bundle)
        store.all().find { it.id == bundle.sourceId }?.let { source ->
            if (manager.installed.none { it.sourceId == source.id }) store.save(source.copy(installedVersion = null))
        }
        refresh()
    }

    /** Apps of [bundle] it did not patch before this install, so updates do not ask again for declined apps. */
    private fun newApps(bundle: InstalledBundle, before: List<InstalledBundle>): Set<String> =
        bundle.apps.keys - before.find { it.id == bundle.id }?.apps?.keys.orEmpty()

    // endregion

    // region Scope

    /** Hooked apps only load bundles if the app is in the module scope. */
    fun requestMissingScope(packages: Collection<String> = _state.value.missingScope) {
        val manager = manager ?: return
        val missing = runCatching { manager.missingScope() }.getOrDefault(emptyList()).filter { it in packages }
        if (missing.isEmpty()) return
        manager.requestScope(missing) { result ->
            _messages.tryEmit(result)
            viewModelScope.launch { refresh() }
        }
    }

    /** Frameworks only put a legacy module's own app in its scope by themselves, so NexAlloy asks for it. */
    private fun requestOwnScope(service: XposedService) {
        if (ownScopeRequested) return
        ownScopeRequested = true
        val packageName = context.packageName
        if (runCatching { packageName in service.scope }.getOrDefault(true)) return
        runCatching { service.requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {}) }
    }

    // endregion

    // region Patches of an app

    /** Loads the patches enabled bundles provide for [packageName]. */
    suspend fun patches(packageName: String): Result<List<BundlePatches>> = withContext(Dispatchers.IO) {
        runCatching {
            val state = _state.value
            val service = state.service ?: error(context.getString(R.string.module_inactive_title))
            val manager = BundleManager(context, service)
            val prefs = service.getRemotePreferences(packageName)
            state.bundles.filter { it.enabled && packageName in it.apps }.map { bundle ->
                val apps: List<AppPatchInfo> = manager.patches(bundle).filter { it.packageName == packageName }
                val patches = apps.flatMap { it.patches.asList() }
                    .filter { it.name.isNotEmpty() && !it.name.startsWith("<") }
                    .sortedBy { it.name }
                    .map { it to prefs.getBoolean(patchPreferenceKey(bundle.id, it.name), it.use) }
                BundlePatches(bundle, patches)
            }
        }
    }

    fun setPatchEnabled(packageName: String, bundle: InstalledBundle, patch: Patch, enabled: Boolean) {
        val service = _state.value.service ?: return
        service.getRemotePreferences(packageName).edit()
            .putBoolean(patchPreferenceKey(bundle.id, patch.name), enabled).apply()
    }

    /** Sets every patch of the app to its default (or to off, if [defaults] is false). */
    fun resetPatches(packageName: String, patches: List<BundlePatches>, defaults: Boolean) {
        val service = _state.value.service ?: return
        val editor = service.getRemotePreferences(packageName).edit()
        for (group in patches) for ((patch, _) in group.patches) {
            editor.putBoolean(patchPreferenceKey(group.bundle.id, patch.name), defaults && patch.use)
        }
        editor.apply()
    }

    // endregion

    // region Play update lock

    /** Where [packageName] stands with the Play update lock. */
    fun guardStatus(packageName: String) = guard.status(packageName)

    /** Locks or releases [packageName], and tells the user how it went. */
    suspend fun setGuard(packageName: String, locked: Boolean) {
        settings.setGuardChoice(packageName, locked)
        val result = if (locked) guard.lock(packageName) else guard.release(packageName)
        val appName = _state.value.apps[packageName] ?: packageName
        result.onSuccess {
            message(if (locked) R.string.guard_locked_message else R.string.guard_released_message, appName)
        }.onFailure {
            message(R.string.guard_failed_message, appName, it.message.orEmpty())
        }
    }

    // endregion

    // region Launcher icon

    private val launcherAlias get() = ComponentName(context, "io.github.nexalloy.ui.MainActivityAlias")

    var launcherIconHidden: Boolean
        get() = context.packageManager.getComponentEnabledSetting(launcherAlias) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        set(hidden) = context.packageManager.setComponentEnabledSetting(
            launcherAlias,
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

    // endregion
}
