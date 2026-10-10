package io.github.nexalloy.activity

import android.app.Activity
import java.util.concurrent.Executors

private val executor = Executors.newCachedThreadPool()

/** Runs [work] off the main thread and delivers its result on the main thread, unless the activity is gone. */
fun <T> Activity.runAsync(work: () -> T, done: (Result<T>) -> Unit) {
    executor.execute {
        val result = runCatching(work)
        runOnUiThread {
            if (!isFinishing && !isDestroyed) done(result)
        }
    }
}
