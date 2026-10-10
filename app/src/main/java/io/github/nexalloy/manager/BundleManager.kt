package io.github.nexalloy.manager

import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.core.BundleClassLoader
import io.github.nexalloy.core.BundleManifest
import io.github.nexalloy.core.BundleRegistry
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.core.InvalidBundleException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Installs, updates and removes patch bundles.
 *
 * Bundle APKs are stored twice: as a remote file of the Xposed service, which hooked apps read,
 * and in the manager's own storage, from which the manager lists their patches.
 */
class BundleManager(private val context: Context, private val service: XposedService) {
    private val registryPrefs get() = service.getRemotePreferences(BundleRegistry.PREFS_GROUP)
    private val localDir get() = File(context.filesDir, "bundles").apply { mkdirs() }

    val installed: List<InstalledBundle> get() = BundleRegistry.read(registryPrefs)

    /** Installs or updates a bundle. Blocking; call off the main thread. */
    fun install(input: InputStream, sourceId: String?): InstalledBundle {
        val tmp = File.createTempFile("bundle", ".apk", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            tmp.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(16)

            val manifest = BundleManifest.read(context.packageManager, tmp)
            var clash = installed.find {
                it.id != manifest.id && manifest.resourcePackageId != 0 && it.resourcePackageId == manifest.resourcePackageId
            }
            // A source that renamed its bundle: the new bundle replaces the old one.
            if (clash != null && sourceId != null && clash.sourceId == sourceId) {
                uninstall(clash)
                clash = null
            }
            if (clash != null) throw InvalidBundleException(
                "${manifest.name} uses the resource package id 0x%02x of ${clash.name}; both cannot be installed."
                    .format(manifest.resourcePackageId)
            )
            val fileName = "${manifest.id}-$hash.apk"
            val local = File(localDir, fileName)
            if (!local.exists()) {
                tmp.copyTo(local, overwrite = true)
            }
            // Fails here, before anything is published, if the bundle's code cannot be loaded.
            val apps = loadApps(local, manifest.entryClass)

            service.openRemoteFile(fileName).use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    output.channel.truncate(0)
                    local.inputStream().use { it.copyTo(output) }
                }
            }

            val previous = installed.find { it.id == manifest.id }
            val bundle = InstalledBundle(
                id = manifest.id,
                name = manifest.name,
                version = manifest.version,
                versionCode = manifest.versionCode,
                author = manifest.author,
                description = manifest.description,
                fileName = fileName,
                enabled = previous?.enabled ?: true,
                sourceId = sourceId ?: previous?.sourceId,
                apps = apps.associate { it.packageName to it.appName },
                resourcePackageId = manifest.resourcePackageId,
            )
            BundleRegistry.write(registryPrefs, installed.filter { it.id != bundle.id } + bundle)
            if (previous != null && previous.fileName != fileName) deleteFiles(previous.fileName)
            return bundle
        } finally {
            tmp.delete()
        }
    }

    fun uninstall(bundle: InstalledBundle) {
        BundleRegistry.write(registryPrefs, installed.filter { it.id != bundle.id })
        deleteFiles(bundle.fileName)
    }

    fun setEnabled(bundle: InstalledBundle, enabled: Boolean) {
        BundleRegistry.write(registryPrefs, installed.map {
            if (it.id == bundle.id) it.copy(enabled = enabled) else it
        })
    }

    /**
     * Installed apps patched by enabled bundles that are not yet in the module scope.
     *
     * Apps that are not installed are left out: the framework asks the user about every requested package.
     */
    fun missingScope(): List<String> {
        val scope = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        return installed.filter { it.enabled }.flatMap { it.apps.keys }.distinct()
            .filter { it !in scope && isInstalled(it) }
    }

    private fun isInstalled(packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun requestScope(packages: List<String>, onResult: (String) -> Unit) {
        service.requestScope(packages, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) =
                onResult("Added to scope: ${approved.joinToString()}")

            override fun onScopeRequestFailed(message: String) = onResult(message)
        })
    }

    /** The patches of an installed bundle, loaded from the manager's copy of the bundle. */
    fun patches(bundle: InstalledBundle): List<AppPatchInfo> {
        val local = File(localDir, bundle.fileName)
        if (!local.exists()) {
            // E.g. after the manager's data was cleared; restore from the remote file.
            service.openRemoteFile(bundle.fileName).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    local.outputStream().use { input.copyTo(it) }
                }
            }
        }
        val manifest = BundleManifest.read(context.packageManager, local)
        return loadApps(local, manifest.entryClass)
    }

    private fun loadApps(apk: File, entryClass: String): List<AppPatchInfo> =
        loadedApps.getOrPut(apk.name) {
            BundleClassLoader.load(apk, entryClass, BundleManager::class.java.classLoader!!).second
        }

    private fun deleteFiles(fileName: String) {
        runCatching { service.deleteRemoteFile(fileName) }
        File(localDir, fileName).delete()
        loadedApps.remove(fileName)
    }

    private companion object {
        /** Bundles are loaded at most once per manager process; their file names change with every update. */
        val loadedApps = mutableMapOf<String, List<AppPatchInfo>>()
    }
}
