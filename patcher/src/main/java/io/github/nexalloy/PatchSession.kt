@file:OptIn(DexKitExperimentalApi::class)

package io.github.nexalloy

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import kotlin.system.measureTimeMillis

@Suppress("UNCHECKED_CAST")
class SharedPrefCache(app: Application) : DexKitCacheBridge.Cache {
    val pref = app.getSharedPreferences("nexalloy_dexkit", MODE_PRIVATE)!!
    private val map = mutableMapOf<String, String>().apply {
        putAll(pref.all as Map<String, String>)
    }

    override fun clearAll() {
        map.clear()
    }

    override fun getString(key: String, default: String?): String? = map.getOrDefault(key, default)

    override fun getAllKeys(): Collection<String> = map.keys

    override fun getStringList(
        key: String, default: List<String>?
    ): List<String>? =
        map.getOrDefault(key, null)?.takeIf(String::isNotBlank)?.split('|') ?: default

    override fun putString(key: String, value: String) {
        map.put(key, value)
    }

    override fun putStringList(key: String, value: List<String>) {
        map.put(key, value.joinToString("|"))
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    fun saveCache() {
        val edit = pref.edit()
        edit.clear()
        map.forEach { (k, v) ->
            edit.putString(k, v)
        }
        edit.commit()
    }
}

/**
 * Applies the patches of every loaded bundle to one app process.
 *
 * Fingerprint matches are cached per bundle and invalidated whenever the app, NexAlloy or the bundle changes.
 */
class PatchSession(
    private val app: Application,
    private val lpparam: XposedModuleInterface.PackageReadyParam,
    private val xposed: XposedInterface,
    /** Identifies the NexAlloy build; part of every cache key. */
    private val coreVersion: String,
    /** Disables the fingerprint cache. */
    private val debug: Boolean,
) {
    private val cache = SharedPrefCache(app)
    private val failedPatches = mutableListOf<String>()
    private val appVersion by lazy { readAppVersion() }

    init {
        System.loadLibrary("dexkit")
        DexKitCacheBridge.init(cache)
    }

    fun apply(bundle: BundleInfo, patches: Array<Patch>) {
        val time = measureTimeMillis {
            loadCacheIfValid(bundle)
            val executor = PatchExecutor(
                app, lpparam, xposed, bundle,
                DexKitCacheBridge.create(bundle.id, lpparam.applicationInfo.sourceDir)
            )
            try {
                executor.executePatches(patches)
            } finally {
                executor.closeDexKit()
            }
            executor.failedPatches.mapTo(failedPatches) { "${it.name} (${bundle.name})" }
        }
        PatcherLog.info { "${lpparam.packageName}: applied ${bundle.id} in ${time}ms" }
    }

    /** Records a bundle that could not be loaded at all. */
    fun reportBundleFailure(bundleName: String, error: Throwable) {
        XposedBridge.log(error)
        failedPatches.add("all patches ($bundleName)")
    }

    fun finish() {
        cache.saveCache()
        if (failedPatches.isNotEmpty()) {
            XposedBridge.log("${lpparam.packageName} version: $appVersion")
            showToast("Error while applying the following patches:\n${failedPatches.joinToString()}")
        } else if (debug) {
            XposedBridge.log("${lpparam.packageName} version: $appVersion")
            showToast("Patches applied successfully")
        }
    }

    private fun loadCacheIfValid(bundle: BundleInfo) {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val idKey = "nexalloy:id:${bundle.id}"
        val id = "${packageInfo.lastUpdateTime}-$coreVersion-${bundle.version}"
        val cachedId = cache.getString(idKey, null)
        val isCached = cachedId == id && !debug

        PatcherLog.info { "${bundle.id} cache ID: $id, cached ID: $cachedId, using cache: $isCached" }

        if (!isCached) {
            DexKitCacheBridge.clearCache(bundle.id)
            cache.putString(idKey, id)
            showToast("NexAlloy is initializing ${bundle.name}, please wait...")
        }
    }

    private fun readAppVersion(): String {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
        return "${packageInfo.versionName} ($versionCode)"
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}
