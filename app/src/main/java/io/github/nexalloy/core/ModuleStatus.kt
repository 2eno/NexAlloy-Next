package io.github.nexalloy.core

/**
 * Whether the module is active, as seen by NexAlloy's own app.
 *
 * NexAlloy lists its own package in its scope; when the module is loaded into the app,
 * [io.github.nexalloy.MainHook] hooks [isActive] to return true.
 */
object ModuleStatus {
    @JvmStatic
    fun isActive(): Boolean = false

    /** Name and version of the Xposed framework, set by the same hook. */
    @JvmStatic
    fun frameworkName(): String? = null
}
