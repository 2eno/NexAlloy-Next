@file:OptIn(DexKitExperimentalApi::class)

package io.github.nexalloy

import android.app.Application
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import io.github.nexalloy.morphe.Fingerprint
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import kotlin.reflect.KProperty0

typealias FindFunc = DexKitBridge.() -> Any
typealias FindClassFunc = DexKitBridge.() -> ClassData
typealias FindMethodFunc = DexKitBridge.() -> MethodData
typealias FindMethodListFunc = DexKitBridge.() -> List<MethodData>
typealias FindFieldFunc = DexKitBridge.() -> FieldData
typealias FindFieldListFunc = DexKitBridge.() -> List<FieldData>

fun patch(
    name: String = "",
    description: String = "",
    use: Boolean = true,
    func: PatchExecutor.() -> Unit
) =
    Patch(name, description, use, func)

class Patch(
    val name: String,
    val description: String,
    val use: Boolean,
    val run: PatchExecutor.() -> Unit
)

abstract class IHook(val xposed: XposedInterface) : XposedInterface by xposed {
    abstract val classLoader: ClassLoader

    fun DexMethod.hookMethod(callback: XC_MethodHook) {
        toMember().hookMethod(callback)
    }

    fun DexMethod.hookMethod(block: HookDsl<IHookCallback>.() -> Unit) {
        toMember().hookMethod(block)
    }

    fun DexClass.toClass() = getInstance(classLoader)
    fun DexMethod.toMethod(): Method {
        var clz = classLoader.loadClass(className)
        do {
            return XposedHelpers.findMethodExactIfExists(clz, name, *paramTypeNames.toTypedArray())
                ?: continue
        } while (clz.superclass.also { clz = it } != null)
        throw NoSuchMethodException("Method $this not found")
    }

    fun DexMethod.toConstructor(): Constructor<*> {
        var clz = classLoader.loadClass(className)
        do {
            return XposedHelpers.findConstructorExactIfExists(clz, *paramTypeNames.toTypedArray())
                ?: continue
        } while (clz.superclass.also { clz = it } != null)
        throw NoSuchMethodException("Method $this not found")
    }

    fun DexMethod.toMember(): Member {
        return when {
            isMethod -> toMethod()
            isConstructor -> toConstructor()
            else -> throw NotImplementedError()
        }
    }

    fun DexField.toField() = getFieldInstance(classLoader)

    override fun getApiVersion(): Int = xposed.apiVersion
}

class DependedHookFailedException(
    subHookName: String, exception: Throwable
) : Exception("Depended hook $subHookName failed.", exception)

/**
 * Applies the patches of one [PatchBundle] to the hooked app.
 *
 * Patches run as extension functions of this class, so everything here is available inside `patch { }`.
 */
class PatchExecutor(
    val appContext: Application,
    val lpparam: XposedModuleInterface.PackageReadyParam,
    xposed: XposedInterface,
    /** The bundle the executed patches come from. */
    val bundle: BundleInfo,
    private val dexkit: DexKitCacheBridge.RecyclableBridge,
) : IHook(xposed) {
    override val classLoader = lpparam.classLoader

    private val patchPreferences = xposed.getRemotePreferences(lpparam.packageName)

    private val appliedPatches = mutableSetOf<Patch>()
    internal val failedPatches = mutableListOf<Patch>()

    internal fun executePatches(patches: Array<Patch>) {
        patches.forEach { hook ->
            if (appliedPatches.contains(hook)) return@forEach
            if (!patchPreferences.getBoolean(patchPreferenceKey(bundle.id, hook.name), hook.use)) return@forEach
            runCatching { hook.run(this) }.onFailure { err ->
                XposedBridge.log(err)
                failedPatches.add(hook)
            }.onSuccess {
                appliedPatches.add(hook)
            }
        }
    }

    internal fun closeDexKit() = dexkit.close()

    fun dependsOn(vararg patches: Patch) {
        patches.forEach { hook ->
            if (appliedPatches.contains(hook)) return@forEach
            runCatching { (hook.run(this)) }.onFailure { err ->
                throw DependedHookFailedException(hook.name, err)
            }.onSuccess {
                appliedPatches.add(hook)
            }
        }
    }

    val KProperty0<FindMethodFunc>.dexMethod
        get() = getDexMethod(this.name, this.get())

    val KProperty0<FindMethodFunc>.method
        get() = dexMethod.toMethod()

    val KProperty0<FindMethodFunc>.constructor
        get() = dexMethod.toConstructor()

    val KProperty0<FindMethodFunc>.member
        get() = dexMethod.toMember()

    val KProperty0<FindMethodFunc>.memberOrNull
        get() = runCatching { this.member }.getOrNull()

    fun KProperty0<FindMethodFunc>.hookMethod(block: HookDsl<IHookCallback>.() -> Unit) {
        dexMethod.hookMethod(block)
    }

    fun KProperty0<FindMethodFunc>.hookMethod(callback: XC_MethodHook) {
        dexMethod.hookMethod(callback)
    }

    val KProperty0<FindMethodListFunc>.dexMethodList
        get() = getDexMethods(this.name, this.get())

    val KProperty0<FindFieldListFunc>.dexFieldList
        get() = getDexFields(this.name, this.get())

    val KProperty0<FindFieldFunc>.dexField
        get() = getDexField(this.name, this.get())

    val KProperty0<FindFieldFunc>.field
        get() = dexField.toField()

    val KProperty0<FindFieldFunc>.declaredClass
        get() = classLoader.loadClass(dexField.declaredClassName)

    val KProperty0<FindFieldFunc>.type
        get() = classLoader.loadClass(dexField.className)

    val KProperty0<FindClassFunc>.dexClass
        get() = getDexClass(this.name, this.get())

    val KProperty0<FindClassFunc>.clazz
        get() = dexClass.toClass()

    // Fingerprint object extensions

    private val Fingerprint.cacheKey
        get() = this::class.simpleName ?: error("Anonymous Fingerprint has no cache key")

    fun Fingerprint.hookMethod(block: HookDsl<IHookCallback>.() -> Unit) {
        getDexMethod(cacheKey) { this@hookMethod.run() }.hookMethod(block)
    }

    fun Fingerprint.hookMethod(callback: XC_MethodHook) {
        getDexMethod(cacheKey) { this@hookMethod.run() }.hookMethod(callback)
    }

    val Fingerprint.dexMethod get() = getDexMethod(cacheKey) { this@dexMethod.run() }

    val Fingerprint.member get() = dexMethod.toMember()

    val Fingerprint.memberOrNull get() = runCatching { this.member }.getOrNull()

    val Fingerprint.method get() = dexMethod.toMethod()

    val Fingerprint.declaredClass get() = classLoader.loadClass(dexMethod.declaredClassName)

    val Fingerprint.constructor get() = dexMethod.toConstructor()

    private inline fun <reified T : Any> wrapFind(
        key: String,
        crossinline funcFunc: DexKitBridge.() -> T,
        crossinline serializer: (T) -> String
    ): DexKitBridge.() -> T? {
        return {
            try {
                funcFunc().also { PatcherLog.info { "$key Matches: ${serializer(it)}" } }
            } catch (e: Exception) {
                PatcherLog.info(e) { "Fingerprint $key Not Found" }
                null
            }
        }
    }

    private inline fun <reified T : Any> wrapFindList(
        key: String,
        crossinline funcFunc: DexKitBridge.() -> List<T>,
        crossinline serializer: (T) -> String
    ): DexKitBridge.() -> List<T> {
        return {
            try {
                funcFunc().also {
                    PatcherLog.info { "$key Matches: ${it.joinToString { serializer(it) }}" }
                }
            } catch (e: Exception) {
                PatcherLog.info(e) { "Fingerprint $key Not Found" }
                emptyList()
            }
        }
    }

    private inline fun getDexClass(
        key: String, crossinline findFunc: DexKitBridge.() -> ClassData
    ): DexClass = dexkit.getClassDirectOrNull(key, wrapFind(key, findFunc) { it.descriptor })!!

    private inline fun getDexMethod(
        key: String, crossinline findFunc: DexKitBridge.() -> MethodData
    ): DexMethod = dexkit.getMethodDirectOrNull(key, wrapFind(key, findFunc) { it.descriptor })!!

    private inline fun getDexField(
        key: String, crossinline findFunc: DexKitBridge.() -> FieldData
    ): DexField = dexkit.getFieldDirectOrNull(key, wrapFind(key, findFunc) { it.descriptor })!!

    private inline fun getDexMethods(
        key: String, crossinline findFunc: DexKitBridge.() -> List<MethodData>
    ): List<DexMethod> = dexkit.getMethodsDirectOrEmpty(
        key, wrapFindList(key, findFunc) { it.descriptor })

    private inline fun getDexFields(
        key: String, crossinline findFunc: DexKitBridge.() -> List<FieldData>
    ): List<DexField> = dexkit.getFieldsDirectOrEmpty(
        key, wrapFindList(key, findFunc) { it.descriptor })
}
