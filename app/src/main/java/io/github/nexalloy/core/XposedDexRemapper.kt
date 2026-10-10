package io.github.nexalloy.core

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.rewriter.DexRewriter
import com.android.tools.smali.dexlib2.rewriter.Rewriter
import com.android.tools.smali.dexlib2.rewriter.RewriterModule
import com.android.tools.smali.dexlib2.rewriter.Rewriters
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.File
import java.util.zip.ZipFile

/**
 * Renames the Xposed API in the code of a bundle to match the framework.
 *
 * Frameworks like LSPosed and Vector rename the Xposed API packages (e.g. `de.robv.android.xposed` to a random
 * package of the same length) and rewrite the module's code to match, but they do not know about code the module
 * loads itself. This rewrites the classes of a bundle APK that use the API into a separate dex file, which is put
 * before the APK in the bundle's class path so its classes take precedence.
 */
class XposedDexRemapper(
    /** Original to actual package prefix in descriptor form, e.g. `Lde/robv/android/xposed/`. */
    private val prefixes: Map<String, String>,
) {
    val isNeeded get() = prefixes.isNotEmpty()

    /** Prefixes of the renamed class names, e.g. `de.robv.android.xposed.` as the framework renamed it. */
    val renamedClassPrefixes: List<String> get() = prefixes.values.map { it.substring(1).replace('/', '.') }

    /** Longest first, so a more specific renaming wins. */
    private val orderedPrefixes = prefixes.entries.sortedByDescending { it.key.length }

    /** Stable key of the renaming, for naming cached files. */
    val key: String get() = prefixes.entries.joinToString("|").hashCode().toUInt().toString(16)

    /**
     * Writes the remapped classes of [apk] to [out].
     *
     * @return false if no class uses the renamed API, in which case nothing is written.
     */
    fun remap(apk: File, out: File): Boolean {
        val opcodes = Opcodes.getDefault()
        val classes = mutableListOf<ClassDef>()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .forEach { entry ->
                    val dex = DexBackedDexFile(opcodes, zip.getInputStream(entry).use { it.readBytes() })
                    dex.classes.filterTo(classes) { usesRenamedApi(it) }
                }
        }
        if (classes.isEmpty()) return false

        val rewriter = DexRewriter(object : RewriterModule() {
            override fun getTypeRewriter(rewriters: Rewriters): Rewriter<String> =
                Rewriter { type -> rename(type) }
        })
        val remapped = classes.map { rewriter.classDefRewriter.rewrite(it) }
        val tmp = File(out.path + ".tmp")
        DexPool.writeTo(FileDataStore(tmp), ImmutableDexFile(opcodes, remapped))
        if (!tmp.renameTo(out)) throw IllegalStateException("Cannot write $out")
        return true
    }

    private fun rename(type: String): String {
        val arrayDepth = type.indexOfFirst { it != '[' }
        val element = type.substring(arrayDepth)
        for ((from, to) in orderedPrefixes) {
            if (element.startsWith(from)) return type.substring(0, arrayDepth) + to + element.substring(from.length)
        }
        return type
    }

    private fun isRenamed(type: String?) = type != null && rename(type) != type

    private fun usesRenamedApi(classDef: ClassDef): Boolean {
        if (isRenamed(classDef.superclass) || classDef.interfaces.any(::isRenamed)) return true
        if (classDef.fields.any { isRenamed(it.type) }) return true
        return classDef.methods.any { method ->
            isRenamed(method.returnType) || method.parameterTypes.any { isRenamed(it.toString()) } ||
                method.implementation?.instructions?.any { instruction ->
                    instruction is ReferenceInstruction && references(instruction.reference)
                } == true ||
                method.implementation?.tryBlocks?.any { block ->
                    block.exceptionHandlers.any { isRenamed(it.exceptionType) }
                } == true
        }
    }

    private fun references(reference: Reference): Boolean = when (reference) {
        is TypeReference -> isRenamed(reference.type)
        is FieldReference -> isRenamed(reference.definingClass) || isRenamed(reference.type)
        is MethodReference -> isRenamed(reference.definingClass) || isRenamed(reference.returnType) ||
            reference.parameterTypes.any { isRenamed(it.toString()) }

        else -> false
    }

    companion object {
        /**
         * Detects the renaming from the names the framework gave the API classes in the module's own code.
         *
         * Each pair is an API class as referenced in the module and its original name.
         */
        fun detect(vararg anchors: Pair<Class<*>, String>): XposedDexRemapper =
            fromNames(anchors.map { (clazz, originalName) -> clazz.name to originalName })

        /** Like [detect], with each pair being the actual and the original name of an API class. */
        fun fromNames(names: List<Pair<String, String>>): XposedDexRemapper {
            val prefixes = mutableMapOf<String, String>()
            for ((actualName, originalName) in names) {
                val original = "L" + originalName.replace('.', '/')
                val actual = "L" + actualName.replace('.', '/')
                // The framework replaces a prefix with a random one of the same length. The prefix need not end at
                // a package, Vector renames `Lio/github/libxposed/api/Xposed` for example, so the renamed part is
                // everything up to the last character that differs.
                if (original == actual || original.length != actual.length) continue
                val end = original.indices.last { original[it] != actual[it] } + 1
                prefixes[original.substring(0, end)] = actual.substring(0, end)
            }
            return XposedDexRemapper(prefixes)
        }
    }
}
