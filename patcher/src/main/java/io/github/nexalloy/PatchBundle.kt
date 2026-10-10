package io.github.nexalloy

/**
 * Version of the patcher API.
 *
 * Bundles record the version they were compiled against (see [PatchBundle]), and NexAlloy refuses
 * to load a bundle built against a newer API than the one it ships. Bump it whenever the API gains
 * something bundles may depend on, and treat removals as breaking changes.
 */
const val PATCHER_API_VERSION = 1

/** The patches a bundle provides for one app. */
class AppPatchInfo(val appName: String, val packageName: String, val patches: Array<Patch>)

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
