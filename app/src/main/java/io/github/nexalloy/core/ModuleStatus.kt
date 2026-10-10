package io.github.nexalloy.core

/**
 * Whether the module is active, as seen by NexAlloy's own app.
 *
 * When the module is loaded into the app, [io.github.nexalloy.MainHook] hooks [isActive] to return true.
 * Frameworks do not put a modern module's own app in its scope by themselves, so NexAlloy requests it.
 */
object ModuleStatus {
    @JvmStatic
    fun isActive(): Boolean = false

    /** Name and version of the Xposed framework, set by the same hook. */
    @JvmStatic
    fun frameworkName(): String? = null
}
