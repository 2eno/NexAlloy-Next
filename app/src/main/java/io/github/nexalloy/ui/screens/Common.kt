package io.github.nexalloy.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** The launcher icon of an installed app, or null if it is not installed. */
fun appIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
}.getOrNull()

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) { appIcon(context, packageName) }
    Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {}
            }
        }
    }
}

fun isInstalled(context: Context, packageName: String) =
    runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
