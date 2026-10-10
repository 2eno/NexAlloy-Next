package io.github.nexalloy.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.nexalloy.manager.SourceClient
import io.github.nexalloy.manager.SourceStore
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
        return Result.success()
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
