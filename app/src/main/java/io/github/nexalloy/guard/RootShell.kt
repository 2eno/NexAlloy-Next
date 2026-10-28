package io.github.nexalloy.guard

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Runs commands through `su` as a chosen user. Blocking; call off the main thread. */
object RootShell {
    class Result(val exitCode: Int, val output: String) {
        val isSuccess get() = exitCode == 0
    }

    const val ROOT_UID = 0

    /** The system user. Package manager commands run as it bypass the installer signature checks. */
    const val SYSTEM_UID = 1000

    /** The user of `adb shell`. */
    const val SHELL_UID = 2000

    /** A way to run a command as a user, by the arguments it passes `su` and what it writes to its stdin. */
    private class Runner(val args: (String) -> List<String>, val stdin: (String) -> String?)

    /** The working runner found for each user, so `su` is only probed once per user. */
    private val runners = ConcurrentHashMap<Int, Runner>()

    /**
     * Runs [command] as the user [uid], like `adb shell` runs as the shell user. su implementations differ, so a
     * way to reach [uid] is probed with `id -u` and cached.
     *
     * @throws IOException if there is no `su`, the user denied root, or [uid] could not be reached.
     */
    fun runAsUid(uid: Int, command: String, timeoutSeconds: Long = 20): Result {
        val runner = runners[uid] ?: detect(uid)?.also { runners[uid] = it }
            ?: throw IOException("Root works, but running as uid $uid failed. Allow it in your root manager.")
        return exec(runner.args(command), runner.stdin(command), timeoutSeconds)
    }

    // su implementations (Magisk, KernelSU, APatch, ...) differ in how they drop to another user.
    private fun candidates(uid: Int) = listOf(
        Runner({ listOf("su", uid.toString(), "-c", it) }, { null }),
        Runner({ listOf("su", "-c", it, uid.toString()) }, { null }),
        Runner({ listOf("su", uid.toString()) }, { it }),
        Runner({ listOf("su", "-u", uid.toString(), "-c", it) }, { null }),
        Runner({ listOf("su", "-c", "setpriv --reuid=$uid --regid=$uid --clear-groups $it") }, { null }),
    )

    /** The first candidate whose `id -u` is [uid], or null if [uid] cannot be reached. */
    private fun detect(uid: Int): Runner? = candidates(uid).firstOrNull { runner ->
        runCatching {
            val result = exec(runner.args("id -u"), runner.stdin("id -u"), 10)
            result.isSuccess && result.output.lineSequence().any { it.trim() == uid.toString() }
        }.getOrDefault(false)
    }

    /** @throws IOException if the process cannot be started (no `su`) or it times out. */
    private fun exec(arguments: List<String>, stdin: String?, timeoutSeconds: Long): Result {
        val process = try {
            ProcessBuilder(arguments).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw IOException("Root access is not available", e)
        }
        // Closing stdin lets the `-c` forms finish; the stdin forms get the command, then exit.
        runCatching { process.outputStream.use { if (stdin != null) it.write("$stdin\nexit\n".toByteArray()) } }
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("Timed out waiting for root access")
        }
        reader.join(1000)
        return Result(process.exitValue(), output.toString().trim())
    }
}
