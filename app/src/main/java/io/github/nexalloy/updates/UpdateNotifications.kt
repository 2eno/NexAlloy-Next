package io.github.nexalloy.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nexalloy.core.R
import io.github.nexalloy.ui.MainActivity

/** Notifications about updates of NexAlloy and of patch sources. */
object UpdateNotifications {
    private const val CHANNEL_APP = "app_updates"
    private const val CHANNEL_PATCHES = "patch_updates"
    private const val ID_APP = 1
    private const val ID_PATCHES = 2
    private const val ID_COMPATIBLE = 3

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_APP, context.getString(R.string.channel_app_updates), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PATCHES, context.getString(R.string.channel_patch_updates), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun appUpdate(context: Context, release: AppRelease) = notify(
        context, CHANNEL_APP, ID_APP,
        context.getString(R.string.notification_app_update_title, release.tag),
        context.getString(R.string.notification_app_update_text),
        MainActivity.DESTINATION_HOME,
    )

    fun patchUpdates(context: Context, sources: List<String>) = notify(
        context, CHANNEL_PATCHES, ID_PATCHES,
        context.getString(R.string.notification_patch_update_title),
        sources.joinToString(),
        MainActivity.DESTINATION_PATCHES,
    )

    /** Apps whose Play updates are locked and whose patches now support a newer version. */
    fun compatibleVersions(context: Context, apps: List<String>) = notify(
        context, CHANNEL_PATCHES, ID_COMPATIBLE,
        context.getString(R.string.notification_compatible_title),
        apps.joinToString(),
        MainActivity.DESTINATION_HOME,
    )

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String, destination: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
