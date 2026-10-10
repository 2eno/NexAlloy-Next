package io.github.nexalloy.common

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.nexalloy.core.BuildConfig
import io.github.nexalloy.activity.runAsync
import io.github.nexalloy.manager.SourceClient
import org.json.JSONObject
import kotlin.random.Random

data class VersionInfo(val versionCode: Int, val versionName: String) {
    companion object {
        fun fromTagName(tagName: String): VersionInfo {
            val split = tagName.split('-', limit = 2)
            return if (split.count() == 2) {
                // VersionCode-VersionName
                VersionInfo(split[0].toIntOrNull() ?: 0, split[1])
            } else {
                // X.Y.Z, Z is versionCode
                VersionInfo(tagName.split('.').last().toIntOrNull() ?: 0, tagName)
            }
        }
    }
}

/** Checks the GitHub releases of NexAlloy itself for updates. */
object UpdateChecker {
    private const val PREFS = "prefs"
    private const val KEY_DISABLE_AUTO_CHECK = "disable_auto_check_update"

    fun isAutoCheckDisabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISABLE_AUTO_CHECK, false)

    fun setAutoCheckDisabled(context: Context, disabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DISABLE_AUTO_CHECK, disabled).apply()

    fun autoCheckUpdate(activity: Activity) {
        if (isAutoCheckDisabled(activity) || Random.nextInt(0, 5) != 0) return
        checkUpdate(activity, silent = true)
    }

    fun checkUpdate(activity: Activity, silent: Boolean) {
        activity.runAsync({
            JSONObject(SourceClient.get("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"))
        }) { result ->
            result.onSuccess { release ->
                val tagName = release.getString("tag_name")
                if (VersionInfo.fromTagName(tagName).versionCode > BuildConfig.VERSION_CODE) {
                    AlertDialog.Builder(activity)
                        .setTitle("NexAlloy $tagName")
                        .setMessage(release.optString("body"))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.getString("html_url"))))
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else if (!silent) {
                    Toast.makeText(activity, "NexAlloy is up to date.", Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                if (!silent) Toast.makeText(activity, "Update check failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
