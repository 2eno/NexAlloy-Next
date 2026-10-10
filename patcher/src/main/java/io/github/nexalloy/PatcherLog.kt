package io.github.nexalloy

import android.util.Log

/** Logging for the patcher itself. Bundles usually log through their own extension code. */
object PatcherLog {
    private const val TAG = "NexAlloy"

    /** Whether [info] messages are logged. Set by NexAlloy for debug builds. */
    @Volatile
    var verbose = false

    fun info(throwable: Throwable? = null, message: () -> String) {
        if (verbose) Log.i(TAG, message(), throwable)
    }

    fun error(throwable: Throwable? = null, message: () -> String) {
        Log.e(TAG, message(), throwable)
    }
}
