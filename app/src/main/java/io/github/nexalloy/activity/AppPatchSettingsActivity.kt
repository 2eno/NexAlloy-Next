@file:Suppress("DEPRECATION")

package io.github.nexalloy.activity

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Button
import io.github.libxposed.service.XposedService
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.core.R
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.manager.BundleManager
import io.github.nexalloy.patchPreferenceKey

class AppPatchSettingsActivity : Activity() {

    companion object {
        const val ARGUMENT_PACKAGE_NAME = "package_name"
        const val ARGUMENT_APP_NAME = "app_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_patch_settings)

        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = intent.getStringExtra(ARGUMENT_APP_NAME)

        if (savedInstanceState != null) return
        val fragment = AppPatchSettingsFragment().apply {
            arguments = Bundle(intent.extras)
        }
        fragmentManager.beginTransaction()
            .replace(R.id.app_patch_settings_container, fragment)
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
    class AppPatchSettingsFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {
        private val packageName get() = arguments.getString(ARGUMENT_PACKAGE_NAME)!!
        private val patchCheckBoxes = mutableListOf<Pair<CheckBoxPreference, Boolean>>()

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
            if (service == null) {
                activity.runOnUiThread { activity.actionBar?.subtitle = "Xposed service unavailable" }
                return
            }
            val manager = BundleManager(activity, service)
            activity.runAsync({
                manager.installed.filter { it.enabled && packageName in it.apps }
                    .map { bundle -> bundle to manager.patches(bundle).filter { it.packageName == packageName } }
            }) { result ->
                result.onSuccess { showPatches(service, it) }
                    .onFailure { activity.actionBar?.subtitle = "Failed to load patches: ${it.message}" }
            }
        }

        private fun showPatches(service: XposedService, bundles: List<Pair<InstalledBundle, List<AppPatchInfo>>>) {
            val remotePrefs = service.getRemotePreferences(packageName)
            val screen = preferenceManager.createPreferenceScreen(context)
            patchCheckBoxes.clear()

            object : Preference(context) {
                @Deprecated("Deprecated in Java")
                override fun onBindView(view: View) {
                    super.onBindView(view)
                    view.findViewById<Button>(R.id.button_default).setOnClickListener {
                        setAllPreferences(remotePrefs) { _, default -> default }
                    }
                    view.findViewById<Button>(R.id.button_none).setOnClickListener {
                        setAllPreferences(remotePrefs) { _, _ -> false }
                    }
                    val isInstalled = runCatching {
                        context.packageManager.getPackageInfo(packageName, 0)
                    }.isSuccess

                    view.findViewById<Button>(R.id.button_app_info).apply {
                        if (!isInstalled) visibility = View.GONE
                        setOnClickListener {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:$packageName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                    }
                }
            }.apply {
                layoutResource = R.layout.preference_header_buttons
                screen.addPreference(this)
            }

            for ((bundle, apps) in bundles) {
                val group: PreferenceGroup = if (bundles.size == 1) screen else PreferenceCategory(context).apply {
                    title = bundle.name
                    screen.addPreference(this)
                }
                for (patch in apps.flatMap { it.patches.asList() }.sortedBy { it.name }) {
                    if (patch.name == "") continue
                    if (patch.name.startsWith("<")) continue
                    CheckBoxPreference(context).apply {
                        key = patchPreferenceKey(bundle.id, patch.name)
                        title = patch.name
                        summary = patch.description
                        isPersistent = false
                        isChecked = remotePrefs.getBoolean(key, patch.use)

                        setOnPreferenceChangeListener { _, newValue ->
                            remotePrefs.edit().putBoolean(key, newValue as Boolean).apply()

                            val vibrator =
                                context.getSystemService(VIBRATOR_SERVICE) as Vibrator?
                            if (vibrator?.hasVibrator() ?: false) {
                                vibrator.vibrate(50)
                            }
                            true
                        }
                        group.addPreference(this)
                        patchCheckBoxes.add(this to patch.use)
                    }
                }
            }

            preferenceScreen = screen
        }

        private fun setAllPreferences(prefs: SharedPreferences, value: (current: Boolean, default: Boolean) -> Boolean) {
            if (!isAdded) return
            val editor = prefs.edit()
            for ((preference, default) in patchCheckBoxes) {
                preference.isChecked = value(preference.isChecked, default)
                editor.putBoolean(preference.key, preference.isChecked)
            }
            editor.apply()
        }
    }
}
