package io.github.nexalloy.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import io.github.nexalloy.core.R

/** An installed app's icon and the color it is drawn in, for the app cards. */
class AppLook(val icon: ImageBitmap?, val color: Color?, val versionName: String?)

fun appLook(context: Context, packageName: String): AppLook = runCatching {
    val pm = context.packageManager
    val info = pm.getPackageInfo(packageName, 0)
    val drawable = pm.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, 144, 144)
    drawable.draw(Canvas(bitmap))
    val palette = Palette.from(bitmap).generate()
    val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
    AppLook(bitmap.asImageBitmap(), swatch?.rgb?.let { Color(it) }, info.versionName)
}.getOrDefault(AppLook(null, null, null))

/** Compares dotted version names numerically, e.g. 21.39.522 < 21.40.161. */
fun compareVersions(a: String, b: String): Int {
    val left = a.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0 }
    val right = b.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val cmp = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
        if (cmp != 0) return cmp
    }
    return 0
}

/**
 * A patched app as a colorful card, like Morphe's app list: a gradient from the app's icon color,
 * its installed version and how that compares to the version the patches recommend.
 */
@Composable
fun AppCard(
    appName: String,
    packageName: String,
    recommendedVersion: String?,
    bundles: String,
    onClick: () -> Unit,
    look: AppLook = run {
        val context = LocalContext.current
        remember(packageName) { appLook(context, packageName) }
    },
) {
    val end = lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.55f)
    val start = lerp(look.color ?: MaterialTheme.colorScheme.primary, Color.Black, 0.25f)
    val gradient = Brush.horizontalGradient(listOf(start, lerp(start, end, 0.6f), end))
    val installed = look.versionName

    val (status, chip) = when {
        installed == null -> Pair(
            recommendedVersion?.let { stringResource(R.string.app_not_installed_recommended, it) }
                ?: stringResource(R.string.app_not_installed_short),
            null,
        )

        recommendedVersion == null -> stringResource(R.string.app_any_version, installed) to null
        compareVersions(installed, recommendedVersion) == 0 ->
            stringResource(R.string.app_version, installed) to (Icons.Filled.Check to stringResource(R.string.chip_recommended))

        compareVersions(installed, recommendedVersion) > 0 ->
            stringResource(R.string.app_newer_than_tested, installed, recommendedVersion) to
                (Icons.Filled.Warning to stringResource(R.string.chip_untested))

        else -> stringResource(R.string.app_older_than_recommended, installed, recommendedVersion) to
            (Icons.Filled.KeyboardArrowUp to recommendedVersion)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.background(gradient).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (look.icon != null) {
                    Image(look.icon, contentDescription = null, modifier = Modifier.size(56.dp))
                } else {
                    Box(
                        Modifier.size(56.dp).background(
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))),
                            CircleShape,
                        )
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appName, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(status, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            bundles, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    chip?.let { (icon, label) -> StatusChip(icon, label) }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f), modifier = Modifier.padding(start = 8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}
