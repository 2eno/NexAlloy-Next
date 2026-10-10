package io.github.nexalloy.core

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XposedDexRemapperTest {
    private val remapper = XposedDexRemapper(
        mapOf("Lde/robv/android/xposed/" to "LJqVep/oVJD/PFvPIBOUUCL/")
    )

    private fun method(name: String, parameterTypes: List<String>, vararg calls: ImmutableMethodReference) =
        ImmutableMethod(
            "Lcom/example/Patch;", name,
            parameterTypes.map { ImmutableMethodParameter(it, null, null) }, "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null,
            ImmutableMethodImplementation(
                parameterTypes.size,
                calls.map { ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, it) } +
                    ImmutableInstruction10x(Opcode.RETURN_VOID),
                null, null
            )
        )

    private fun apk(dir: File): File {
        val log = ImmutableMethodReference(
            "Lde/robv/android/xposed/XposedBridge;", "log", listOf("Ljava/lang/String;"), "V"
        )
        val usesApi = ImmutableClassDef(
            "Lcom/example/Patch;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null,
            listOf(
                method("hook", listOf("Lde/robv/android/xposed/XC_MethodHook\$MethodHookParam;")),
                method("log", emptyList(), log),
            )
        )
        val plain = ImmutableClassDef(
            "Lcom/example/Plain;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", null, null, null, null, null
        )
        val dex = File(dir, "classes.dex")
        DexPool.writeTo(FileDataStore(dex), ImmutableDexFile(Opcodes.getDefault(), listOf(usesApi, plain)))
        val apk = File(dir, "bundle.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(dex.readBytes())
            zip.closeEntry()
        }
        return apk
    }

    @Test
    fun remapsOnlyClassesUsingTheApi() {
        val dir = createTempDirectory().toFile()
        val out = File(dir, "remapped.dex")
        assertTrue(remapper.remap(apk(dir), out))

        val dex = DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(out.toPath()))
        val classes = dex.classes.associateBy { it.type }
        assertEquals(setOf("Lcom/example/Patch;"), classes.keys)
        val methods = classes.getValue("Lcom/example/Patch;").methods.associateBy { it.name }
        assertEquals(
            listOf("LJqVep/oVJD/PFvPIBOUUCL/XC_MethodHook\$MethodHookParam;"),
            methods.getValue("hook").parameterTypes.map { it.toString() }
        )
        val call = methods.getValue("log").implementation!!.instructions
            .filterIsInstance<ReferenceInstruction>().single().reference as MethodReference
        assertEquals("LJqVep/oVJD/PFvPIBOUUCL/XposedBridge;", call.definingClass)
    }

    @Test
    fun nothingToRemap() {
        val dir = createTempDirectory().toFile()
        val identity = XposedDexRemapper(mapOf("Lio/github/libxposed/api/" to "Lab/cdefghi/jklmnop/qrs/"))
        assertFalse(identity.remap(apk(dir), File(dir, "remapped.dex")))
    }

    @Test
    fun detect() {
        assertFalse(XposedDexRemapper.detect(String::class.java to "java.lang.String").isNeeded)
        assertFalse(XposedDexRemapper.detect(String::class.java to "de.robv.android.xposed.String").isNeeded)
    }

    /** Vector renames `Lio/github/libxposed/api/Xposed`, part of the class names, and may put slashes anywhere. */
    @Test
    fun detectsPrefixesWithinClassNames() {
        val detected = XposedDexRemapper.fromNames(
            listOf(
                "JqVep.oVJD.PFvPIBOUUCL.XposedBridge" to "de.robv.android.xposed.XposedBridge",
                "pLIi.MEOLeMaE.JomTEIJ.zOjoOjHyInterface" to "io.github.libxposed.api.XposedInterface",
            )
        )
        assertEquals(
            setOf("JqVep.oVJD.PFvPIBOUUCL", "pLIi.MEOLeMaE.JomTEIJ.zOjoOjHy"),
            detected.renamedClassPrefixes.toSet()
        )
        val dir = createTempDirectory().toFile()
        val out = File(dir, "remapped.dex")
        assertTrue(detected.remap(apk(dir), out))
        val dex = DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(out.toPath()))
        val hook = dex.classes.single().methods.single { it.name == "hook" }
        assertEquals(
            listOf("LJqVep/oVJD/PFvPIBOUUCL/XC_MethodHook\$MethodHookParam;"),
            hook.parameterTypes.map { it.toString() }
        )
    }

    /** Run with -Dbundle=/path/to/bundle.nxb to measure a real bundle. */
    @Test
    fun realBundle() {
        val bundle = System.getProperty("bundle")?.let(::File) ?: return
        val out = File(createTempDirectory().toFile(), "remapped.dex")
        val time = System.currentTimeMillis()
        assertTrue(remapper.remap(bundle, out))
        val dex = DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(out.toPath()))
        println("Remapped ${dex.classes.size} classes (${out.length()} bytes) in ${System.currentTimeMillis() - time}ms")
        assertTrue(dex.classes.none { c -> c.type.startsWith("Lde/robv/android/xposed/") })
    }
}
