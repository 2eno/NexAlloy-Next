package io.github.nexalloy.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.nexalloy.guard.GuardPolicy
import io.github.nexalloy.guard.PlayUpdateGuard
import io.github.nexalloy.manager.SourceClient
import io.github.nexalloy.manager.SourceStore
import io.github.nexalloy.ui.screens.compareVersions
import java.util.concurrent.TimeUnit

/** Checks for updates of NexAlloy and of the installed patch sources in the background, and notifies about them. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = AppSettings(context)

        if (settings.notifyAppUpdates) {
            runCatching { AppUpdateChecker.latest() }.getOrNull()
                ?.takeIf { it.isNewer && settings.lastNotified(KEY_APP) != it.tag }
                ?.let { release ->
                    UpdateNotifications.appUpdate(context, release)
                    settings.setLastNotified(KEY_APP, release.tag)
                }
        }

        if (settings.notifyPatchUpdates) {
            val updated = SourceStore(context).all()
                .filter { it.installedVersion != null }
                .mapNotNull { source ->
                    val release = runCatching { SourceClient.latestRelease(source) }.getOrNull() ?: return@mapNotNull null
                    val key = "source_${source.id}"
                    if (release.version == source.installedVersion || settings.lastNotified(key) == release.version) {
                        return@mapNotNull null
                    }
                    settings.setLastNotified(key, release.version)
                    "${source.name} ${release.version}"
                }
            if (updated.isNotEmpty()) UpdateNotifications.patchUpdates(context, updated)
        }

        val guard = PlayUpdateGuard(context, settings)
        runCatching { guard.reapply() }
        if (settings.notifyPatchUpdates) notifyCompatibleVersions(context, settings, guard)
        return Result.success()
    }

    /** Tells about apps held back by the Play update lock whose patches now support a newer version. */
    private fun notifyCompatibleVersions(context: Context, settings: AppSettings, guard: PlayUpdateGuard) {
        val choices = settings.guardChoices
        val apps = settings.recommendedVersions.mapNotNull { (packageName, recommended) ->
            if (!GuardPolicy.wanted(choices[packageName], true, settings.guardByDefault)) return@mapNotNull null
            if (!guard.status(packageName).locked) return@mapNotNull null
            val installed = runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                ?: return@mapNotNull null
            val key = "compatible_$packageName"
            if (compareVersions(installed, recommended) >= 0 || settings.lastNotified(key) == recommended) return@mapNotNull null
            settings.setLastNotified(key, recommended)
            val label = runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
            "$label v$recommended"
        }
        if (apps.isNotEmpty()) UpdateNotifications.compatibleVersions(context, apps)
    }

    companion object {
        private const val WORK_NAME = "update_check"
        private const val KEY_APP = "app"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
