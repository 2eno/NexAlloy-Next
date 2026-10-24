package io.github.nexalloy.guard

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Runs commands as root through `su`. Blocking; call off the main thread. */
object RootShell {
    class Result(val exitCode: Int, val output: String) {
        val isSuccess get() = exitCode == 0
    }

    /** The user of `adb shell`. Package manager commands like `pm set-installer` only accept it, not root. */
    const val SHELL_UID = 2000

    /**
     * Runs [command] as the shell user through root, like `adb shell`. su implementations differ in where they take
     * the user, so every form is tried, then plain root as the last resort. The first one that works wins.
     */
    fun runAsShell(command: String, timeoutSeconds: Long = 20): Result {
        val quoted = "'" + command.replace("'", "'\\''") + "'"
        val attempts = listOf(
            listOf("su", "-c", command, SHELL_UID.toString()),
            listOf("su", SHELL_UID.toString(), "-c", command),
            listOf("su", "-c", "su $SHELL_UID -c $quoted"),
            listOf("su", "-c", command),
        )
        var last: Result? = null
        for (arguments in attempts) {
            val result = run(arguments, timeoutSeconds)
            if (result.isSuccess) return result
            last = result
        }
        return last!!
    }

    fun run(command: String, timeoutSeconds: Long = 20) = run(listOf("su", "-c", command), timeoutSeconds)

    /** @throws IOException if there is no `su`, or the user denied root. */
    private fun run(arguments: List<String>, timeoutSeconds: Long): Result {
        val process = try {
            ProcessBuilder(arguments).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw IOException("Root access is not available", e)
        }
        process.outputStream.close()
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
