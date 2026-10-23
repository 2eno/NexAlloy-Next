package io.github.nexalloy.updates

import android.content.Context
import org.json.JSONObject

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

    // region Play update lock, see PlayUpdateGuard

    /** Whether apps with a recommended version are locked unless the user chose otherwise. */
    var guardByDefault: Boolean
        get() = prefs.getBoolean("guard_by_default", true)
        set(value) = prefs.edit().putBoolean("guard_by_default", value).apply()

    /** Whether root worked once. Locks are only applied in the background after that. */
    var rootVerified: Boolean
        get() = prefs.getBoolean("root_verified", false)
        set(value) = prefs.edit().putBoolean("root_verified", value).apply()

    /** The lock the user chose per app, true for locked and false for released. */
    val guardChoices: Map<String, Boolean>
        get() = readMap("guard_choices") { it as Boolean }

    fun setGuardChoice(packageName: String, locked: Boolean) = writeMap("guard_choices", guardChoices + (packageName to locked))

    /** The recommended version of each patched app, as of the last time the bundles were read. */
    var recommendedVersions: Map<String, String>
        get() = readMap("recommended_versions") { it as String }
        set(value) = writeMap("recommended_versions", value)

    private fun <T> readMap(key: String, convert: (Any) -> T): Map<String, T> = runCatching {
        val json = JSONObject(prefs.getString(key, null) ?: return emptyMap())
        json.keys().asSequence().associateWith { convert(json.get(it)) }
    }.getOrDefault(emptyMap())

    private fun writeMap(key: String, map: Map<String, Any>) =
        prefs.edit().putString(key, JSONObject(map).toString()).apply()

    // endregion
}
