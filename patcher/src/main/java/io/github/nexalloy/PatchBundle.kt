package io.github.nexalloy

/**
 * Version of the patcher API.
 *
 * Bundles record the version they were compiled against (see [PatchBundle]), and NexAlloy refuses
 * to load a bundle built against a newer API than the one it ships. Bump it whenever the API gains
 * something bundles may depend on, and treat removals as breaking changes.
 */
const val PATCHER_API_VERSION = 3

/**
 * An app version the patches were tested with, like Morphe's `AppTarget`.
 *
 * @param experimental The patches may work, but are not known to fully support this version.
 */
class AppTarget @JvmOverloads constructor(val version: String, val experimental: Boolean = false)

/**
 * The patches a bundle provides for one app.
 *
 * @param targets App versions the patches were tested with, newest first. NexAlloyNext recommends the newest one
 *   that is not experimental. Empty if the patches work with any version. Since patcher API 2.
 * @param appIconColor Brand color of the app as 0xRRGGBB, like Morphe's `appIconColor`; the app's card starts with
 *   it. Null for the default card colors. Since patcher API 3.
 */
class AppPatchInfo @JvmOverloads constructor(
    val appName: String,
    val packageName: String,
    val patches: Array<Patch>,
    val targets: List<AppTarget> = emptyList(),
    val appIconColor: Int? = null,
) {
    /** The version NexAlloyNext recommends, or null for any version. */
    val recommendedVersion: String?
        get() = (targets.firstOrNull { !it.experimental } ?: targets.firstOrNull())?.version
}

/**
 * Entry point of a patch bundle.
 *
 * A bundle is an APK whose manifest names the implementing class in the `nexalloy.bundle.entry`
 * meta-data. The class must have a public no-arg constructor or be a Kotlin `object`.
 */
interface PatchBundle {
    /** The apps this bundle patches. */
    val apps: List<AppPatchInfo>
}

/** A patch bundle loaded into the current process. */
class BundleInfo(
    /** Package name of the bundle APK, unique per bundle. */
    val id: String,
    val name: String,
    val version: String,
    /** Path of the bundle APK, also used as its resource path. */
    val apkPath: String,
    val classLoader: ClassLoader,
)

/** Key in the per-app remote preferences that stores whether a patch is enabled. */
fun patchPreferenceKey(bundleId: String, patchName: String) = "$bundleId/$patchName"
