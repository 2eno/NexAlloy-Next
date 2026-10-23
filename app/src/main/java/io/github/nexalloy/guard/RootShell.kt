package io.github.nexalloy.guard

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Runs commands as root through `su`. Blocking; call off the main thread. */
object RootShell {
    class Result(val exitCode: Int, val output: String) {
        val isSuccess get() = exitCode == 0
    }

    /** @throws IOException if there is no `su`, or the user denied root. */
    fun run(command: String, timeoutSeconds: Long = 20): Result {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
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
