package io.github.nexalloy

import android.app.Application
import android.widget.Toast
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.nexalloy.core.BuildConfig
import io.github.nexalloy.core.BundleClassLoader
import io.github.nexalloy.core.BundleManifest
import io.github.nexalloy.core.BundleParentClassLoader
import io.github.nexalloy.core.BundleRegistry
import io.github.nexalloy.core.InstalledBundle
import io.github.nexalloy.core.ModuleStatus
import io.github.nexalloy.core.XposedDexRemapper
import io.github.nexalloy.morphe.ResourceFinder
import io.github.nexalloy.morphe.resourceMappings
import java.io.File
import java.io.FileInputStream

class MainHook : XposedModule() {
    private var targetPackageName: String? = null

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        if (param.packageName == moduleApplicationInfo.packageName) {
            reportActive(param.classLoader)
            return
        }
        if (targetPackageName != null && targetPackageName != param.packageName) return

        val bundles = runCatching {
            BundleRegistry.read(getRemotePreferences(BundleRegistry.PREFS_GROUP))
        }.getOrDefault(emptyList()).filter { it.enabled && param.packageName in it.apps }
        if (bundles.isEmpty()) return
        targetPackageName = param.packageName

        inContext(param) { app ->
            if (isPatchedApp(param)) {
                Toast.makeText(app, "NexAlloy does not work with patched apps", Toast.LENGTH_LONG).show()
                return@inContext
            }

            resourceMappings = object : ResourceFinder {
                override operator fun get(type: String, name: String): Int {
                    val id = app.resources.getIdentifier(name, type, app.packageName)
                    if (id == 0) throw Exception("Could not find resource type: $type name: $name")
                    return id
                }
            }
            PatcherLog.verbose = BuildConfig.DEBUG

            val session = PatchSession(app, param, this, BuildConfig.COMMIT_HASH, BuildConfig.DEBUG)
            val loaded = bundles.mapNotNull { bundle ->
                runCatching { loadBundle(app, bundle, bundles) }
                    .onFailure { session.reportBundleFailure(bundle.name, it) }
                    .getOrNull()
            }
            // Register every bundle's resources before any patch adds them to a context.
            loaded.forEach { (info, _) -> BundleResources.register(info.apkPath) }
            loaded.forEach { (info, patches) -> session.apply(info, patches) }
            session.finish()
        }
    }

    /**
     * Copies the bundle APK from the module's remote files into the app's code cache and loads it.
     *
     * The copy is named after the remote file, which changes with every bundle update.
     */
    private fun loadBundle(
        app: Application,
        bundle: InstalledBundle,
        activeBundles: List<InstalledBundle>,
    ): Pair<BundleInfo, Array<Patch>> {
        val dir = File(app.codeCacheDir, "nexalloy").apply { mkdirs() }
        dir.listFiles()?.forEach { file ->
            // Bundle APKs and the dex files remapped from them start with the bundle's file name.
            if (activeBundles.none { file.name.startsWith(it.fileName) } || file.name.endsWith(".tmp")) file.delete()
        }

        val apk = File(dir, bundle.fileName)
        if (!apk.exists()) {
            val tmp = File(dir, "${bundle.fileName}.tmp")
            openRemoteFile(bundle.fileName).use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
            }
            if (!tmp.renameTo(apk)) throw IllegalStateException("Cannot store bundle ${bundle.id}")
        }

        val manifest = BundleManifest.read(app.packageManager, apk)
        val (classLoader, apps) = BundleClassLoader.load(
            apk, manifest.entryClass, bundleParentClassLoader, remappedDex(dir, apk)
        )
        val patches = apps.filter { it.packageName == app.packageName }
            .flatMap { it.patches.asList() }.toTypedArray()
        return BundleInfo(manifest.id, manifest.name, manifest.version, apk.path, classLoader) to patches
    }

    /**
     * The framework may rename the Xposed API, see [XposedDexRemapper]. Detected from this module's own code,
     * which the framework rewrote. The original names are assembled at runtime so the framework cannot rewrite them.
     */
    private val remapper by lazy {
        fun name(vararg parts: String) = parts.joinToString(".")
        XposedDexRemapper.detect(
            XposedBridge::class.java to name("de", "robv", "android", "xposed", "XposedBridge"),
            XposedInterface::class.java to name("io", "github", "libxposed", "api", "XposedInterface"),
        )
    }

    private val bundleParentClassLoader by lazy {
        BundleParentClassLoader(
            MainHook::class.java.classLoader!!,
            listOf(XposedBridge::class.java, XposedInterface::class.java).map { it.name.substringBeforeLast('.') + "." } +
                remapper.renamedClassPrefixes,
        )
    }

    /** The bundle's classes using the renamed Xposed API, rewritten to match it, or null if none need it. */
    private fun remappedDex(dir: File, apk: File): File? {
        if (!remapper.isNeeded) return null
        val dex = File(dir, "${apk.name}-${remapper.key}.dex")
        // Marks bundles without classes to remap, so they are not scanned again.
        val none = File(dir, "${apk.name}-${remapper.key}.none")
        if (dex.exists()) return dex
        if (none.exists()) return null
        val time = System.currentTimeMillis()
        val remapped = remapper.remap(apk, dex)
        PatcherLog.info { "Remapped the Xposed API in ${apk.name} in ${System.currentTimeMillis() - time}ms" }
        if (!remapped) none.createNewFile()
        return dex.takeIf { remapped }
    }

    /** Tells NexAlloy's own app that the module is active, see [ModuleStatus]. */
    private fun reportActive(classLoader: ClassLoader) {
        val status = classLoader.loadClass(ModuleStatus::class.java.name)
        val framework = "$frameworkName $frameworkVersion"
        status.getMethod("isActive").hookMethod(XC_MethodReplacement.returnConstant(true))
        status.getMethod("frameworkName").hookMethod(XC_MethodReplacement.returnConstant(framework))
    }

    private fun isPatchedApp(param: PackageReadyParam): Boolean = listOf(
        "app.morphe.extension.shared.Utils",
        "app.morphe.extension.shared.utils.Utils",
        "app.revanced.integrations.shared.Utils",
        "app.revanced.integrations.shared.utils.Utils",
        "app.revanced.extension.shared.Utils",
        // Patched with 2eno Patches.
        "app.twoeno.extension.shared.Logger",
    ).any { runCatching { param.classLoader.loadClass(it) }.isSuccess }
}

context(xposed: XposedInterface)
fun inContext(lpparam: PackageReadyParam, f: (Application) -> Unit) {
    val appClassName = lpparam.applicationInfo.className ?: Application::class.java.name
    val appClazz = XposedHelpers.findClass(appClassName, lpparam.classLoader)
    appClazz.getMethod("onCreate").hookMethod {
        before {
            f(it.thisObject as Application)
        }
    }
}
