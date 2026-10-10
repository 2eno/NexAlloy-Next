package io.github.nexalloy.updates

import android.content.Context

/** NexAlloy's own settings, in its private preferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var notifyAppUpdates: Boolean
        get() = prefs.getBoolean("notify_app_updates", true)
        set(value) = prefs.edit().putBoolean("notify_app_updates", value).apply()

    var notifyPatchUpdates: Boolean
        get() = prefs.getBoolean("notify_patch_updates", true)
        set(value) = prefs.edit().putBoolean("notify_patch_updates", value).apply()

    /** Version last notified about per update key, so every version notifies once. */
    fun lastNotified(key: String): String? = prefs.getString("notified_$key", null)

    fun setLastNotified(key: String, version: String) =
        prefs.edit().putString("notified_$key", version).apply()
}
