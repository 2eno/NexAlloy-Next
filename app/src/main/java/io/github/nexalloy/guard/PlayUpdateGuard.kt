package io.github.nexalloy.guard

import android.content.Context
import android.os.Build
import io.github.nexalloy.updates.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Keeps the Play Store from updating patched apps by itself.
 *
 * The Play Store only updates apps it installed. A locked app has NexAlloyNext set as its installer instead, which
 * needs root (`pm set-installer`). Releasing a lock gives the app back to the Play Store.
 */
class PlayUpdateGuard(private val context: Context, private val settings: AppSettings) {

    /** Where an app stands. */
    class Status(val installed: Boolean, val installer: String?, val locked: Boolean)

    /** The package that installed [packageName], or null. */
    fun installerOf(packageName: String): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    fun isInstalled(packageName: String) =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    fun status(packageName: String): Status {
        val installed = isInstalled(packageName)
        val installer = if (installed) installerOf(packageName) else null
        return Status(installed, installer, locked = installer == context.packageName)
    }

    /** Locks [packageName]. Fails with a readable message if root is missing or the installer did not change. */
    suspend fun lock(packageName: String): Result<Unit> = setInstaller(packageName, context.packageName)

    /** Gives [packageName] back to the Play Store. */
    suspend fun release(packageName: String): Result<Unit> = setInstaller(packageName, PLAY_STORE)

    private suspend fun setInstaller(packageName: String, installer: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidPackageName(packageName)) { "Invalid package name $packageName" }
            if (!isInstalled(packageName)) throw IOException("$packageName is not installed")
            val result = RootShell.runAsShell("pm set-installer $packageName $installer")
            if (!result.isSuccess) throw IOException(shorten(result.output).ifEmpty { "pm set-installer failed (${result.exitCode})" })
            // Root worked, whatever the installer does next.
            settings.rootVerified = true
            val actual = installerOf(packageName)
            if (actual != installer) {
                throw IOException("The installer is still ${actual ?: "unknown"}, this Android does not allow changing it")
            }
        }
    }

    /**
     * Locks the apps that should be locked and are not, e.g. after the Play Store reinstalled one.
     * Does nothing until root worked once, so no root prompt appears out of the blue.
     *
     * @return the number of apps locked.
     */
    suspend fun reapply(): Int {
        if (!settings.rootVerified) return 0
        var locked = 0
        for (packageName in packagesToLock()) {
            if (lock(packageName).isSuccess) locked++
        }
        return locked
    }

    private fun packagesToLock(): List<String> {
        val recommended = settings.recommendedVersions
        val choices = settings.guardChoices
        return (recommended.keys + choices.keys).distinct().filter { packageName ->
            val wanted = GuardPolicy.wanted(choices[packageName], packageName in recommended, settings.guardByDefault)
            wanted && status(packageName).let { it.installed && !it.locked }
        }
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"

        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        /** The first lines of a command's output, without the stack trace Android prints for exceptions. */
        fun shorten(output: String): String =
            output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("at ") }
                .take(2).joinToString(" ").take(240)

        /** Package names go into a root shell command. */
        fun isValidPackageName(packageName: String) = PACKAGE_NAME.matches(packageName)
    }
}

/** Which apps are locked. */
object GuardPolicy {
    /**
     * @param choice What the user chose for the app, or null.
     * @param hasRecommendedVersion Whether a bundle names a version the patches were tested with.
     * @param defaultOn The setting that locks apps with a recommended version unless the user chose otherwise.
     */
    fun wanted(choice: Boolean?, hasRecommendedVersion: Boolean, defaultOn: Boolean): Boolean =
        choice ?: (defaultOn && hasRecommendedVersion)
}
