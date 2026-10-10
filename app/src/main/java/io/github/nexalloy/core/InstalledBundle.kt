package io.github.nexalloy.core

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * A patch bundle installed through the manager.
 *
 * The registry of installed bundles lives in the remote preferences, so hooked apps know which
 * bundles target them without loading every bundle. The bundle APK itself is a remote file.
 */
data class InstalledBundle(
    /** Package name of the bundle APK. */
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    /** Name of the remote file holding the bundle APK. */
    val fileName: String,
    val enabled: Boolean,
    /** Id of the [PatchSource] the bundle was installed from, or null if imported from a file. */
    val sourceId: String?,
    /** Package name to display name of every app the bundle patches. */
    val apps: Map<String, String>,
    /** Package id of the bundle's resources, 0 if unknown. */
    val resourcePackageId: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("version", version)
        .put("versionCode", versionCode)
        .put("author", author)
        .put("description", description)
        .put("fileName", fileName)
        .put("enabled", enabled)
        .put("sourceId", sourceId)
        .put("apps", JSONObject(apps))
        .put("resourcePackageId", resourcePackageId)

    companion object {
        fun fromJson(json: JSONObject) = InstalledBundle(
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            versionCode = json.optLong("versionCode"),
            author = json.optString("author"),
            description = json.optString("description"),
            fileName = json.getString("fileName"),
            enabled = json.optBoolean("enabled", true),
            sourceId = json.optString("sourceId").takeIf { it.isNotEmpty() && it != "null" },
            apps = json.getJSONObject("apps").let { apps ->
                apps.keys().asSequence().associateWith { apps.getString(it) }
            },
            resourcePackageId = json.optInt("resourcePackageId"),
        )
    }
}

/** Reads and writes the installed bundles in the shared remote preferences. */
object BundleRegistry {
    /** Remote preferences group shared with hooked apps. */
    const val PREFS_GROUP = "bundles"
    private const val KEY = "installed"

    fun read(prefs: SharedPreferences): List<InstalledBundle> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { InstalledBundle.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun write(prefs: SharedPreferences, bundles: List<InstalledBundle>) {
        val array = JSONArray()
        bundles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).commit()
    }
}
