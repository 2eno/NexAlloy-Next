/*
 * The app card design is ported from Morphe Manager (HomeAppCards.kt, StatusBadge.kt, PressFeedback.kt and
 * AppIcon.kt), Copyright 2026 Morphe, https://github.com/MorpheApp/morphe-manager, licensed under the GPLv3.
 */

package io.github.nexalloy.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.nexalloy.core.R
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Path as AndroidPath

/** Morphe's brand colors, which the cards fade into from the app's own color. */
private val GradientMid = Color(0xFF1E5AA8)
private val GradientEnd = Color(0xFF00AFAE)

/** Card colors of apps whose bundle declares no color. */
private val DefaultColors = listOf(Color(0xFF0E3F6E), GradientMid, GradientEnd)

/** The gradient of an app's card, from the color its bundle declares (0xRRGGBB). */
fun appCardColors(appIconColor: Int?): List<Color> =
    appIconColor?.let { listOf(Color(it or (0xFF shl 24)), GradientMid, GradientEnd) } ?: DefaultColors

// Version names longer than this would leave the badge no room, so it says "Update" instead
private const val MAX_BADGE_VERSION_LENGTH = 10

/** An installed app's icon and version, for the app cards. */
class AppLook(val icon: ImageBitmap?, val versionName: String?)

fun appLook(context: Context, packageName: String): AppLook = runCatching {
    val pm = context.packageManager
    val info = pm.getPackageInfo(packageName, 0)
    val drawable = pm.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, 192, 192)
    drawable.draw(Canvas(bitmap))
    AppLook(bitmap.asImageBitmap(), info.versionName)
}.getOrDefault(AppLook(null, null))

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

private class CardStyle(
    val titleColor: Color,
    val subtitleColor: Color,
    val chipContainerColor: Color,
    val chipContentColor: Color,
    val titleStyle: TextStyle,
    val subtitleStyle: TextStyle,
    val iconSize: Dp = 60.dp,
    val cardRadius: Dp = 24.dp,
    val cardHeight: Dp = 80.dp,
    val contentPadding: Dp = 16.dp,
    val contentSpacing: Dp = 16.dp,
)

@Composable
private fun cardStyle(colors: List<Color>, subtitleAlpha: Float = 0.75f): CardStyle {
    // White reads on the colors a bundle declares, but a light one would swallow it
    val content = if (colors.blend().luminance() < 0.5f) Color.White else Color.Black
    return CardStyle(
        titleColor = content,
        subtitleColor = content.copy(alpha = subtitleAlpha),
        chipContainerColor = content.copy(alpha = 0.20f),
        chipContentColor = content,
        titleStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 2f), blurRadius = 4f),
        ),
        subtitleStyle = MaterialTheme.typography.bodyMedium.copy(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 2f),
        ),
    )
}

private fun List<Color>.blend(): Color = Color(
    red = sumOf { it.red.toDouble() }.toFloat() / size,
    green = sumOf { it.green.toDouble() }.toFloat() / size,
    blue = sumOf { it.blue.toDouble() }.toFloat() / size,
)

/**
 * A patched app on the home screen, as Morphe Manager draws its apps: a frosted glass card in the app's color with
 * its icon, installed version and badges for what needs doing.
 *
 * @param inScope Whether the app is in the module scope, without which no patch applies.
 */
@Composable
fun AppCard(
    appName: String,
    packageName: String,
    appIconColor: Int?,
    recommendedVersion: String?,
    inScope: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: AppLook = run {
        val context = LocalContext.current
        remember(packageName) { appLook(context, packageName) }
    },
) {
    val colors = remember(appIconColor) { appCardColors(appIconColor) }
    val installed = look.versionName
    if (installed == null) {
        NotInstalledAppCard(appName, colors, recommendedVersion, onClick, modifier)
        return
    }
    val style = cardStyle(colors, subtitleAlpha = 0.85f)

    val notInScopeLabel = stringResource(R.string.badge_not_in_scope)
    val untestedLabel = stringResource(R.string.chip_untested)
    val updateLabel = stringResource(R.string.badge_update)
    val versionCompare = recommendedVersion?.let { compareVersions(installed, it) }
    val version = "v$installed"
    val description = buildString {
        append("$appName, $version")
        if (!inScope) append(", $notInScopeLabel")
        if (versionCompare != null && versionCompare < 0) append(", $updateLabel v$recommendedVersion")
        if (versionCompare != null && versionCompare > 0) append(", $untestedLabel")
    }

    AppCardLayout(
        colors = colors,
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = description
        },
    ) {
        AppIcon(look.icon, colors, Modifier.size(style.iconSize))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                appName, style = style.titleStyle, color = style.titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                // Badge height is reserved whether a badge shows, so the name above stays put
                modifier = Modifier.height(statusBadgeHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    version, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = style.subtitleStyle, color = style.subtitleColor,
                )
                if (!inScope) {
                    StatusBadge(notInScopeLabel, Icons.Outlined.AutoFixHigh, style.chipContainerColor, style.chipContentColor)
                }
                when {
                    versionCompare == null -> Unit
                    versionCompare < 0 -> StatusBadge(
                        recommendedVersion.takeIf { it.length <= MAX_BADGE_VERSION_LENGTH }?.let { "v$it" } ?: updateLabel,
                        Icons.Outlined.ArrowUpward, style.chipContainerColor, style.chipContentColor,
                    )

                    versionCompare > 0 -> StatusBadge(
                        untestedLabel, Icons.AutoMirrored.Outlined.HelpOutline,
                        style.chipContainerColor, style.chipContentColor,
                    )
                }
            }
        }
    }
}

/** Card for an app the bundles patch that is not installed. */
@Composable
private fun NotInstalledAppCard(
    appName: String,
    colors: List<Color>,
    recommendedVersion: String?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val style = cardStyle(colors)
    val subtitle = listOfNotNull(
        recommendedVersion?.let { "v$it" },
        stringResource(R.string.app_not_installed_short),
    ).joinToString(" • ")
    AppCardLayout(
        colors = colors,
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "$appName, $subtitle"
        },
    ) {
        AppIcon(null, colors, Modifier.size(style.iconSize))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                appName, style = style.titleStyle, color = style.titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                modifier = Modifier.height(statusBadgeHeight).wrapContentHeight(Alignment.CenterVertically),
                style = style.subtitleStyle, color = style.subtitleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The frosted glass card: a diagonal sweep from the bottom-start tint to the top-end accent, and a gradient border.
 */
@Composable
private fun AppCardLayout(
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val style = cardStyle(colors)
    val shape = RoundedCornerShape(style.cardRadius)
    val view = LocalView.current
    val baseColor = colors.first()
    val midColor = colors.getOrElse(1) { baseColor }
    val endColor = colors.last()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(style.cardHeight)
            .pressScale(interactionSource)
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val cr = CornerRadius(style.cardRadius.toPx())
                val rtl = layoutDirection == LayoutDirection.Rtl
                val startX = if (rtl) w else 0f
                val endX = if (rtl) 0f else w

                val glass = Brush.linearGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.70f),
                        midColor.copy(alpha = 0.58f),
                        endColor.copy(alpha = 0.64f),
                    ),
                    start = Offset(startX, h),
                    end = Offset(endX, 0f),
                )
                // Bright top-start to faded bottom-end; a flat card gets an even edge instead
                val border = if (colors.all { it == baseColor }) {
                    SolidColor(style.titleColor.copy(alpha = 0.30f))
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            midColor.copy(alpha = 0.30f),
                            endColor.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.20f),
                        ),
                        start = Offset(startX, 0f),
                        end = Offset(endX, h),
                    )
                }
                val borderStroke = Stroke(width = 1.5.dp.toPx())

                onDrawWithContent {
                    drawRoundRect(brush = glass, cornerRadius = cr)
                    drawContent()
                    drawRoundRect(brush = border, cornerRadius = cr, style = borderStroke)
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = style.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(style.contentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The app's icon, or a glass placeholder in the system icon shape when it is not installed. */
@Composable
private fun AppIcon(icon: ImageBitmap?, colors: List<Color>, modifier: Modifier) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier)
        return
    }
    val baseColor = colors.first()
    val midColor = colors.getOrElse(1) { baseColor }
    val endColor = colors.last()
    Box(
        modifier = modifier
            .wrapContentSize()
            .fillMaxSize(AppIconContentFraction)
            .drawWithCache {
                val outline = AppIconShape.createOutline(size, layoutDirection, this)
                val rtl = layoutDirection == LayoutDirection.Rtl
                val startX = if (rtl) size.width else 0f
                val endX = if (rtl) 0f else size.width
                val glass = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f),
                        baseColor.copy(alpha = 0.22f),
                        endColor.copy(alpha = 0.20f),
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, size.height),
                )
                val border = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        midColor.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.35f),
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, size.height),
                )
                val borderStroke = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    drawOutline(outline, brush = glass)
                    drawOutline(outline, brush = border, style = borderStroke)
                }
            }
    )
}

/** Side of the square an [AdaptiveIconDrawable.getIconMask] is declared in. */
private const val ICON_MASK_SIZE = 100f

/** Share of its slot an adaptive icon's visible part covers: the inner 72dp of its 108dp. */
private const val AppIconContentFraction = 72f / 108f

/** The system mask for app icons (circle, squircle, etc.), so placeholders match the icons. */
private val AppIconShape: Shape = object : Shape {
    private val mask: AndroidPath? by lazy { runCatching { AdaptiveIconDrawable(null, null).iconMask }.getOrNull() }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val mask = mask ?: return Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), CornerRadius(size.minDimension / 2))
        )
        val matrix = AndroidMatrix().apply { setScale(size.width / ICON_MASK_SIZE, size.height / ICON_MASK_SIZE) }
        val path: Path = AndroidPath(mask).apply { transform(matrix) }.asComposePath()
        return Outline.Generic(path)
    }
}

private val BadgeHorizontalPadding = 10.dp
private val BadgeVerticalPadding = 4.dp
private val BadgeIconSize = 14.dp
private val BadgeItemSpacing = 5.dp

/** Height a [StatusBadge] occupies at the current font scale. */
private val statusBadgeHeight: Dp
    @Composable get() {
        val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
        return maxOf(labelHeight, BadgeIconSize) + BadgeVerticalPadding * 2
    }

/** Inline status marker on a card, a frosted pill that reads on any card color. */
@Composable
private fun StatusBadge(text: String, icon: ImageVector, containerColor: Color, contentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .padding(horizontal = BadgeHorizontalPadding, vertical = BadgeVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, contentDescription = null, tint = contentColor,
            modifier = Modifier.padding(end = BadgeItemSpacing).size(BadgeIconSize),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Medium)
    }
}

/** How far a card sinks while held. */
private const val PressedScale = 0.97f

@Composable
private fun rememberPressScale(interactionSource: InteractionSource): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_press_scale",
    )
}

/** Sinks the node while [interactionSource] reports a press. */
@Composable
private fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val scale = rememberPressScale(interactionSource)
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
