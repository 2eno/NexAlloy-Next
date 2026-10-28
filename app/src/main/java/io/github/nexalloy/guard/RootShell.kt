package io.github.nexalloy.guard

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Runs commands through `su`, as root or as the shell user. Blocking; call off the main thread. */
object RootShell {
    class Result(val exitCode: Int, val output: String) {
        val isSuccess get() = exitCode == 0
    }

    /** The user of `adb shell` (uid 2000). Package manager commands reject root but accept it. */
    const val SHELL_UID = 2000

    /** A way to run a command as the shell user, by the arguments it passes `su` and what it writes to its stdin. */
    private class ShellRunner(val args: (String) -> List<String>, val stdin: (String) -> String?)

    @Volatile private var shellRunner: ShellRunner? = null

    // su implementations (Magisk, KernelSU, APatch, ...) differ in how they drop to another user, so each candidate
    // is probed once and the first that actually reaches uid 2000 is kept.
    private val candidates = listOf(
        ShellRunner({ listOf("su", SHELL_UID.toString(), "-c", it) }, { null }),
        ShellRunner({ listOf("su", "-c", it, SHELL_UID.toString()) }, { null }),
        ShellRunner({ listOf("su", SHELL_UID.toString()) }, { it }),
        ShellRunner({ listOf("su", "-u", SHELL_UID.toString(), "-c", it) }, { null }),
        ShellRunner({ listOf("su", "-c", "setpriv --reuid=$SHELL_UID --regid=$SHELL_UID --clear-groups $it") }, { null }),
    )

    /**
     * Runs [command] as the shell user (uid 2000), like `adb shell` does. Package manager commands such as
     * `pm set-installer` fail as root with "Unknown calling UID: 0" and are only accepted from the shell user.
     *
     * @throws IOException if there is no `su`, the user denied root, or no way to become the shell user worked.
     */
    fun runAsShell(command: String, timeoutSeconds: Long = 20): Result {
        val runner = shellRunner ?: detectShellRunner() ?: throw IOException(
            "Root works, but running as the shell user (uid 2000) failed. Allow it in your root manager."
        )
        shellRunner = runner
        return exec(runner.args(command), runner.stdin(command), timeoutSeconds)
    }

    /** Runs [command] as root. */
    fun run(command: String, timeoutSeconds: Long = 20): Result =
        exec(listOf("su", "-c", command), null, timeoutSeconds)

    /** The first candidate whose `id -u` is 2000, or null if none can reach the shell user. */
    private fun detectShellRunner(): ShellRunner? = candidates.firstOrNull { runner ->
        runCatching {
            val result = exec(runner.args("id -u"), runner.stdin("id -u"), 10)
            result.isSuccess && result.output.lineSequence().any { it.trim() == SHELL_UID.toString() }
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
