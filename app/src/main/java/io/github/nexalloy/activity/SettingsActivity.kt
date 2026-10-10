@file:Suppress("DEPRECATION")

package io.github.nexalloy.activity

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.window.OnBackInvokedDispatcher
import io.github.libxposed.service.XposedService
import io.github.nexalloy.core.BuildConfig
import io.github.nexalloy.core.R
import io.github.nexalloy.common.UpdateChecker
import io.github.nexalloy.core.ModuleStatus
import io.github.nexalloy.manager.BundleManager
import kotlin.system.exitProcess

class SettingsActivity : Activity(), SettingApplication.ServiceStateListener {

    private var mService: XposedService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                onBackPressed()
            }
        }
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayShowHomeEnabled(true)

        if (savedInstanceState != null) return

        fragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment())
            .commit()
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
        mService = service
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.xp_settings_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
        menu.findItem(R.id.menu_hide_icon).isChecked =
            packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        menu.findItem(R.id.menu_disable_auto_check).isChecked = UpdateChecker.isAutoCheckDisabled(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_hide_icon -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
                val status = if (newChecked) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                packageManager.setComponentEnabledSetting(
                    aliasName,
                    status,
                    PackageManager.DONT_KILL_APP
                )
                true
            }

            R.id.menu_disable_auto_check -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                UpdateChecker.setAutoCheckDisabled(this, newChecked)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
        exitProcess(0)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    class SettingsFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {
        private var mService: XposedService? = null

        private var offPreference: Preference? = null
        private var onCategory: PreferenceCategory? = null

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val rootScreen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = rootScreen

            Preference(context).apply {
                setSummary(R.string.slogan_summary)
                isEnabled = false
                rootScreen.addPreference(this)
            }

            Preference(context).apply {
                if (ModuleStatus.isActive()) {
                    setTitle(R.string.module_active_title)
                    summary = ModuleStatus.frameworkName()
                } else {
                    setTitle(R.string.module_inactive_title)
                    setSummary(R.string.module_inactive_summary)
                }
                isSelectable = false
                rootScreen.addPreference(this)
            }

            Preference(context).apply {
                setTitle(R.string.faq_title)
                intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/${BuildConfig.UPDATE_REPOSITORY}#readme")
                )
                rootScreen.addPreference(this)
            }

            addPreferencesFromResource(R.xml.license_prefs)

            Preference(context).apply {
                setTitle(R.string.check_for_update_title)
                summary =
                    """Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_HASH}) ${BuildConfig.BUILD_TYPE}
                       |Build Date: ${DateUtils.getRelativeTimeSpanString(BuildConfig.COMMIT_DATE * 1000)}""".trimMargin()
                setOnPreferenceClickListener {
                    UpdateChecker.checkUpdate(activity, silent = false)
                    true
                }
                rootScreen.addPreference(this)
            }
            UpdateChecker.autoCheckUpdate(activity)

            updateDynamicUI(null)
        }

        override fun onResume() {
            super.onResume()
            // Bundles may have changed in the sources screen.
            updateDynamicUI(mService)
        }

        fun updateDynamicUI(service: XposedService?) {
            val rootScreen = preferenceScreen ?: return
            if (onCategory != null) rootScreen.removePreference(onCategory)
            if (offPreference != null) rootScreen.removePreference(offPreference)

            if (service == null) {
                offPreference = Preference(context).apply {
                    setSummary(R.string.module_not_activated_summary)
                    isEnabled = false
                    rootScreen.addPreference(this)
                }
                return
            }

            val bundles = runCatching { BundleManager(context, service).installed }.getOrDefault(emptyList())
            onCategory = PreferenceCategory(context).apply {
                setTitle(R.string.patch_selection)
                order = 1
                rootScreen.addPreference(this)

                addPreference(Preference(context).apply {
                    setTitle(R.string.patch_sources_title)
                    summary = resources.getQuantityString(
                        R.plurals.installed_bundles_summary, bundles.size, bundles.size
                    )
                    intent = Intent(context, SourcesActivity::class.java)
                })

                if (bundles.none { it.enabled }) return@apply

                addPreference(Preference(context).apply {
                    setSummary(R.string.force_stop_to_apply_summary)
                    isEnabled = false
                })

                bundles.filter { it.enabled }
                    .flatMap { it.apps.entries }
                    .associate { it.key to it.value }
                    .entries.sortedBy { it.value.lowercase() }
                    .forEach { (packageName, appName) ->
                        addPreference(Preference(context).apply {
                            title = appName
                            summary = packageName
                            intent = Intent(context, AppPatchSettingsActivity::class.java)
                                .putExtra(AppPatchSettingsActivity.ARGUMENT_PACKAGE_NAME, packageName)
                                .putExtra(AppPatchSettingsActivity.ARGUMENT_APP_NAME, appName)
                        })
                    }
            }
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
            val activated = service?.takeIf {
                runCatching { it.getRemotePreferences(io.github.nexalloy.core.BundleRegistry.PREFS_GROUP) }.isSuccess
            }
            mService = activated
            activity?.runOnUiThread { updateDynamicUI(activated) }
        }
    }
}
