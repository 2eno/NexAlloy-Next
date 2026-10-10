@file:Suppress("DEPRECATION")

package io.github.nexalloy.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import io.github.libxposed.service.XposedService
import io.github.nexalloy.core.R
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.manager.BundleManager
import io.github.nexalloy.manager.PatchSource
import io.github.nexalloy.manager.SourceClient
import io.github.nexalloy.manager.SourceRelease
import io.github.nexalloy.manager.SourceStore

/** Manages patch sources and the bundles installed from them. */
class SourcesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_patch_settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setTitle(R.string.patch_sources_title)

        if (savedInstanceState != null) return
        fragmentManager.beginTransaction()
            .replace(R.id.app_patch_settings_container, SourcesFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    class SourcesFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {
        private var manager: BundleManager? = null
        private lateinit var store: SourceStore

        /** Latest release of each source, or the error fetching it. */
        private val releases = mutableMapOf<String, Result<SourceRelease>>()
        private val busy = mutableSetOf<String>()

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            store = SourceStore(context)
            preferenceScreen = preferenceManager.createPreferenceScreen(context)
        }

        override fun onStart() {
            super.onStart()
            SettingApplication.addServiceStateListener(this, true)
        }

        override fun onStop() {
            SettingApplication.removeServiceStateListener(this)
            super.onStop()
        }

        override fun onServiceStateChanged(service: XposedService?) {
            val activity = activity ?: return
            activity.runOnUiThread {
                manager = service?.let { BundleManager(activity, it) }
                refresh()
                if (releases.isEmpty()) checkUpdates()
            }
        }

        private fun checkUpdates() {
            for (source in store.all()) fetchRelease(source)
        }

        private fun fetchRelease(source: PatchSource, then: ((SourceRelease) -> Unit)? = null) {
            activity.runAsync({ SourceClient.latestRelease(source) }) { result ->
                releases[source.id] = result
                refresh()
                result.onSuccess { then?.invoke(it) }
                    .onFailure { if (then != null) toast(getString(R.string.source_error, it.message)) }
            }
        }

        private fun refresh() {
            if (!isAdded) return
            val screen = preferenceScreen
            screen.removeAll()
            val manager = manager

            Preference(context).apply {
                setSummary(R.string.patch_sources_summary)
                isEnabled = false
                screen.addPreference(this)
            }

            if (manager == null) {
                Preference(context).apply {
                    setSummary(R.string.module_not_activated_summary)
                    isEnabled = false
                    screen.addPreference(this)
                }
                return
            }

            val installed = runCatching { manager.installed }.getOrDefault(emptyList())

            val missingScope = runCatching { manager.missingScope() }.getOrDefault(emptyList())
            if (missingScope.isNotEmpty()) {
                Preference(context).apply {
                    setTitle(R.string.scope_missing_title)
                    summary = getString(R.string.scope_missing_summary, missingScope.joinToString())
                    setOnPreferenceClickListener {
                        manager.requestScope(missingScope) { message ->
                            activity?.runOnUiThread { toast(message); refresh() }
                        }
                        true
                    }
                    screen.addPreference(this)
                }
            }

            PreferenceCategory(context).apply {
                setTitle(R.string.sources_category)
                screen.addPreference(this)
                for (source in store.all()) addPreference(sourcePreference(source))
                addPreference(Preference(context).apply {
                    setTitle(R.string.add_source_title)
                    setOnPreferenceClickListener { showAddSourceDialog(); true }
                })
                addPreference(Preference(context).apply {
                    setTitle(R.string.import_bundle_title)
                    setSummary(R.string.import_bundle_summary)
                    setOnPreferenceClickListener {
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*"),
                            REQUEST_IMPORT
                        )
                        true
                    }
                })
            }

            PreferenceCategory(context).apply {
                setTitle(R.string.installed_bundles_category)
                screen.addPreference(this)
                if (installed.isEmpty()) {
                    addPreference(Preference(context).apply {
                        setSummary(R.string.no_bundles_summary)
                        isEnabled = false
                    })
                }
                for (bundle in installed.sortedBy { it.name.lowercase() }) {
                    addPreference(bundlePreference(bundle))
                }
            }
        }

        private fun sourcePreference(source: PatchSource) = Preference(context).apply {
            title = source.name
            val release = releases[source.id]
            val status = when {
                source.id in busy -> getString(R.string.source_installing)
                release == null -> getString(R.string.source_checking)
                release.isFailure -> getString(R.string.source_error, release.exceptionOrNull()?.message)
                source.installedVersion == null -> getString(R.string.source_available, release.getOrThrow().version)
                source.installedVersion != release.getOrThrow().version ->
                    getString(R.string.source_update_available, source.installedVersion, release.getOrThrow().version)

                else -> getString(R.string.source_up_to_date, source.installedVersion)
            }
            summary = "${source.url}\n$status"
            setOnPreferenceClickListener { showSourceDialog(source); true }
        }

        private fun bundlePreference(bundle: InstalledBundle) = Preference(context).apply {
            title = "${bundle.name} ${bundle.version}"
            val apps = bundle.apps.values.sortedBy { it.lowercase() }.joinToString()
            val state = if (bundle.enabled) "" else getString(R.string.bundle_disabled) + "\n"
            summary = state + (if (bundle.author.isNotEmpty()) "${bundle.author}\n" else "") + apps
            setOnPreferenceClickListener { showBundleDialog(bundle); true }
        }

        private fun showSourceDialog(source: PatchSource) {
            val release = releases[source.id]?.getOrNull()
            val actions = buildList {
                add(getString(if (source.installedVersion == null) R.string.action_install else R.string.action_update) to {
                    install(source)
                })
                if (release != null && release.changelog.isNotBlank()) add(getString(R.string.action_changelog) to {
                    AlertDialog.Builder(activity)
                        .setTitle("${source.name} ${release.version}")
                        .setMessage(release.changelog)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    Unit
                })
                val page = release?.pageUrl ?: source.githubRepository?.let { "https://github.com/$it" }
                if (page != null) add(getString(R.string.action_open_page) to {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
                })
                add(getString(R.string.action_remove_source) to {
                    store.remove(source.id)
                    refresh()
                })
            }
            AlertDialog.Builder(activity)
                .setTitle(source.name)
                .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
                .show()
        }

        private fun install(source: PatchSource) {
            val manager = manager ?: return
            busy.add(source.id)
            refresh()
            val before = manager.installed
            activity.runAsync({
                val release = SourceClient.latestRelease(source)
                val bundles = release.bundles.map { (_, url) ->
                    SourceClient.open(url).inputStream.use { manager.install(it, source.id) }
                }
                store.save(source.copy(installedVersion = release.version))
                release to bundles
            }) { result ->
                busy.remove(source.id)
                result.onSuccess { (release, bundles) ->
                    releases[source.id] = Result.success(release)
                    toast(getString(R.string.bundles_installed, bundles.joinToString { "${it.name} ${it.version}" }))
                    requestMissingScope(bundles.flatMap { newApps(it, before) })
                }.onFailure { toast(getString(R.string.install_failed, it.message)) }
                refresh()
            }
        }

        private fun showAddSourceDialog() {
            val padding = (20 * resources.displayMetrics.density).toInt()
            val name = EditText(context).apply { setHint(R.string.source_name_hint) }
            val url = EditText(context).apply { setHint(R.string.source_url_hint) }
            val prerelease = CheckBox(context).apply { setText(R.string.source_prerelease) }
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
                addView(name)
                addView(url)
                addView(prerelease)
            }
            AlertDialog.Builder(activity)
                .setTitle(R.string.add_source_title)
                .setMessage(R.string.add_source_warning)
                .setView(layout)
                .setPositiveButton(R.string.action_add) { _, _ ->
                    val urlText = url.text.toString().trim()
                    if (urlText.isEmpty()) return@setPositiveButton
                    val source = PatchSource.create(
                        name.text.toString().trim().ifEmpty { urlText.substringAfterLast('/') },
                        urlText,
                        prerelease.isChecked,
                    )
                    store.save(source)
                    refresh()
                    fetchRelease(source) { install(store.get(source.id) ?: source) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun showBundleDialog(bundle: InstalledBundle) {
            val manager = manager ?: return
            val details = buildString {
                append(getString(R.string.bundle_details, bundle.id, bundle.version))
                if (bundle.description.isNotEmpty()) append("\n\n").append(bundle.description)
                append("\n\n").append(bundle.apps.entries.joinToString("\n") { "${it.value} (${it.key})" })
            }
            AlertDialog.Builder(activity)
                .setTitle(bundle.name)
                .setMessage(details)
                .setPositiveButton(if (bundle.enabled) R.string.action_disable else R.string.action_enable) { _, _ ->
                    manager.setEnabled(bundle, !bundle.enabled)
                    if (!bundle.enabled) requestMissingScope(bundle.apps.keys)
                    refresh()
                }
                .setNegativeButton(R.string.action_uninstall) { _, _ ->
                    manager.uninstall(bundle)
                    store.all().find { it.id == bundle.sourceId }?.let {
                        if (manager.installed.none { installed -> installed.sourceId == it.id }) {
                            store.save(it.copy(installedVersion = null))
                        }
                    }
                    refresh()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
            val uri = data?.data ?: return
            val manager = manager ?: return
            val resolver = context.contentResolver
            val before = manager.installed
            activity.runAsync({
                resolver.openInputStream(uri)!!.use { manager.install(it, null) }
            }) { result ->
                result.onSuccess {
                    toast(getString(R.string.bundles_installed, "${it.name} ${it.version}"))
                    requestMissingScope(newApps(it, before))
                }.onFailure { toast(getString(R.string.install_failed, it.message)) }
                refresh()
            }
        }

        /**
         * Apps of [bundle] it did not patch before this install, so an update does not ask again
         * for apps the user already declined.
         */
        private fun newApps(bundle: InstalledBundle, before: List<InstalledBundle>): Set<String> =
            bundle.apps.keys - before.find { it.id == bundle.id }?.apps?.keys.orEmpty()

        /** Hooked apps only load bundles if the app is in the module scope. */
        private fun requestMissingScope(packages: Collection<String>) {
            val manager = manager ?: return
            val missing = runCatching { manager.missingScope() }.getOrDefault(emptyList()).filter { it in packages }
            if (missing.isEmpty()) return
            manager.requestScope(missing) { message ->
                activity?.runOnUiThread { toast(message); refresh() }
            }
        }

        private fun toast(message: String) {
            val context = activity ?: return
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        private companion object {
            const val REQUEST_IMPORT = 1
        }
    }
}
