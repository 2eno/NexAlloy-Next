package io.github.nexalloy.guard

import android.content.Context
import android.os.Build
import io.github.nexalloy.ui.screens.compareVersions
import io.github.nexalloy.updates.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Keeps the Play Store from updating patched apps by itself.
 *
 * The Play Store only updates apps it installed. A locked app is reinstalled from its own APKs with the adb shell
 * as its installer (`pm install -r -i`, as the shell user through root), so it looks sideloaded; its data and uid
 * stay. Releasing a lock reinstalls it with the Play Store as installer again.
 *
 * Once a bundle recommends a newer version than the installed one, the lock is released so the Play Store updates
 * the app, and it is locked again after that update.
 *
 * `pm set-installer` cannot do this: it requires the caller to share a signature with the old installer, which
 * for apps from the Play Store only Google's own apps do.
 */
class PlayUpdateGuard(private val context: Context, private val settings: AppSettings) {

    /** Where an app stands. */
    class Status(val installed: Boolean, val installer: String?, val locked: Boolean)

    /** What [apply] did. */
    enum class Action { NONE, LOCKED, RELEASED, UPDATE_ALLOWED }

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
        return Status(installed, installer, locked = installed && installer != PLAY_STORE)
    }

    /** Whether the user wants [packageName] locked, by their choice or by default. */
    fun wanted(packageName: String) = GuardPolicy.wanted(
        settings.guardChoices[packageName],
        settings.recommendedVersions.containsKey(packageName),
        settings.guardByDefault,
    )

    /** The recommended version of [packageName] if it is newer than the installed one, which the Play Store may install. */
    fun supportedNewerVersion(packageName: String): String? {
        val recommended = settings.recommendedVersions[packageName] ?: return null
        val installed = runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            ?: return null
        return recommended.takeIf { GuardPolicy.updateSupported(installed, it) }
    }

    /**
     * Brings [packageName] into the state the user wants: locked, or released so the Play Store installs a newer
     * version the patches support, or released because the user does not want it locked.
     */
    suspend fun apply(packageName: String): Result<Action> {
        val status = status(packageName)
        if (!status.installed) return Result.success(Action.NONE)
        return when {
            !wanted(packageName) ->
                if (status.locked) release(packageName).map { Action.RELEASED } else Result.success(Action.NONE)
            supportedNewerVersion(packageName) != null ->
                if (status.locked) release(packageName).map { Action.UPDATE_ALLOWED } else Result.success(Action.NONE)
            else ->
                if (status.locked) Result.success(Action.NONE) else lock(packageName).map { Action.LOCKED }
        }
    }

    /** Locks [packageName]: the Play Store does not update it by itself. Restarts the app. */
    suspend fun lock(packageName: String): Result<Unit> = setInstaller(packageName, SHELL_PACKAGE)

    /** Gives [packageName] back to the Play Store. Restarts the app. */
    suspend fun release(packageName: String): Result<Unit> = setInstaller(packageName, PLAY_STORE)

    private suspend fun setInstaller(packageName: String, installer: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidPackageName(packageName)) { "Invalid package name $packageName" }
            val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }
                .getOrElse { throw IOException("$packageName is not installed") }
            val command = reinstallCommand(listOf(info.sourceDir) + info.splitSourceDirs.orEmpty(), installer)
            var firstError: String? = null
            for (uid in PRIVILEGED_UIDS) {
                val result = runCatching { RootShell.runAsUid(uid, command, REINSTALL_TIMEOUT_SECONDS) }
                    .getOrElse { if (firstError == null) firstError = it.message; null } ?: continue
                settings.rootVerified = true
                if (result.isSuccess && installerOf(packageName) == installer) return@runCatching
                val error = shorten(result.output).ifEmpty { "pm install failed (${result.exitCode})" }
                if (firstError == null) firstError = error
            }
            throw IOException(firstError ?: "Could not reinstall $packageName as root")
        }
    }

    /** What [reapply] did. */
    class Reapplied(val locked: List<String>, val updatesAllowed: Map<String, String>)

    /**
     * Applies the lock to every app that should be locked: locks it, e.g. after the Play Store reinstalled it, or
     * releases it when the patches support a newer version. Does nothing until root worked once, so no root prompt
     * appears out of the blue.
     */
    suspend fun reapply(): Reapplied {
        if (!settings.rootVerified) return Reapplied(emptyList(), emptyMap())
        val locked = mutableListOf<String>()
        val updatesAllowed = mutableMapOf<String, String>()
        val candidates = (settings.recommendedVersions.keys + settings.guardChoices.keys).distinct()
        for (packageName in candidates.filter(::wanted)) {
            when (apply(packageName).getOrNull()) {
                Action.LOCKED -> locked += packageName
                Action.UPDATE_ALLOWED -> supportedNewerVersion(packageName)?.let { updatesAllowed[packageName] = it }
                else -> Unit
            }
        }
        return Reapplied(locked, updatesAllowed)
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"

        /** The users tried for the reinstall: installs by the shell user (like adb) may name any installer. */
        val PRIVILEGED_UIDS = listOf(RootShell.SHELL_UID, RootShell.ROOT_UID)

        /** Copying and installing a large app like Instagram takes a while. */
        private const val REINSTALL_TIMEOUT_SECONDS = 300L

        private const val STAGING_DIR = "/data/local/tmp/nexalloy-lock"

        /**
         * The shell command reinstalling an app from its [apks] (base first, then splits) with [installer] as its
         * installer. The APKs are copied first, as the originals are replaced during the install.
         */
        fun reinstallCommand(apks: List<String>, installer: String): String {
            require(isValidPackageName(installer)) { "Invalid installer $installer" }
            val copies = apks.indices.map { "$STAGING_DIR/$it.apk" }
            return listOf(
                "rm -rf $STAGING_DIR",
                "mkdir -p $STAGING_DIR || exit 1",
                "trap 'rm -rf $STAGING_DIR' EXIT",
                apks.zip(copies).joinToString(" && ") { (apk, copy) -> "cp ${quote(apk)} $copy" } + " || exit 1",
                "pm install -r -i $installer ${copies.joinToString(" ")}",
            ).joinToString("; ")
        }

        private fun quote(path: String) = "'" + path.replace("'", "'\\''") + "'"

        /** The adb shell package, used as the installer of a locked app. Platform-signed, so the shell user can set it. */
        const val SHELL_PACKAGE = "com.android.shell"

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

    /** Whether the patches support a newer version than [installed], so the Play Store may update to it. */
    fun updateSupported(installed: String, recommended: String): Boolean = compareVersions(installed, recommended) < 0
}
