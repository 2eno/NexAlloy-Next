package io.github.nexalloy.guard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayUpdateGuardTest {
    @Test
    fun policy() {
        // The user's choice wins over the default.
        assertEquals(true, GuardPolicy.wanted(true, hasRecommendedVersion = false, defaultOn = false))
        assertEquals(false, GuardPolicy.wanted(false, hasRecommendedVersion = true, defaultOn = true))
        // Without a choice, apps with a recommended version are locked by default.
        assertEquals(true, GuardPolicy.wanted(null, hasRecommendedVersion = true, defaultOn = true))
        assertEquals(false, GuardPolicy.wanted(null, hasRecommendedVersion = false, defaultOn = true))
        assertEquals(false, GuardPolicy.wanted(null, hasRecommendedVersion = true, defaultOn = false))
    }

    @Test
    fun packageNamesAreSafeForTheShell() {
        assertTrue(PlayUpdateGuard.isValidPackageName("com.instagram.android"))
        assertTrue(PlayUpdateGuard.isValidPackageName("com.google.android.apps.youtube.music"))
        assertTrue(PlayUpdateGuard.isValidPackageName("io.github.nexalloy.next"))
        assertFalse(PlayUpdateGuard.isValidPackageName("com.example; reboot"))
        assertFalse(PlayUpdateGuard.isValidPackageName("com.example && id"))
        assertFalse(PlayUpdateGuard.isValidPackageName("`id`"))
        assertFalse(PlayUpdateGuard.isValidPackageName("nodots"))
        assertFalse(PlayUpdateGuard.isValidPackageName(""))
    }

    @Test
    fun shortensExceptions() {
        val output = "Exception occurred while executing 'set-installer':\njava.lang.SecurityException: Unknown calling UID: 0\n  at com.android.server.pm.X.y(X.java:1)\n  at z"
        assertEquals(
            "Exception occurred while executing 'set-installer': java.lang.SecurityException: Unknown calling UID: 0",
            PlayUpdateGuard.shorten(output),
        )
        assertEquals("", PlayUpdateGuard.shorten(""))
    }

    @Test
    fun reinstallCommand() {
        val command = PlayUpdateGuard.reinstallCommand(
            listOf("/data/app/~~a==/com.example-b==/base.apk", "/data/app/~~a==/com.example-b==/split_config.arm64_v8a.apk"),
            "com.android.shell",
        )
        assertEquals(
            "rm -rf /data/local/tmp/nexalloy-lock; mkdir -p /data/local/tmp/nexalloy-lock || exit 1; " +
                "trap 'rm -rf /data/local/tmp/nexalloy-lock' EXIT; " +
                "cp '/data/app/~~a==/com.example-b==/base.apk' /data/local/tmp/nexalloy-lock/0.apk && " +
                "cp '/data/app/~~a==/com.example-b==/split_config.arm64_v8a.apk' /data/local/tmp/nexalloy-lock/1.apk || exit 1; " +
                "pm install -r -i com.android.shell /data/local/tmp/nexalloy-lock/0.apk /data/local/tmp/nexalloy-lock/1.apk",
            command,
        )
        assertEquals("Failure [INSTALL_FAILED_X: y]", PlayUpdateGuard.shorten("Failure [INSTALL_FAILED_X: y]"))
    }

    @Test
    fun updatesToSupportedVersions() {
        // The patches support a newer version: the Play Store may update.
        assertTrue(GuardPolicy.updateSupported("446.0.0.1.1", "447.0.0.55.81"))
        // Installed is the recommended version, or newer than tested: stay locked.
        assertFalse(GuardPolicy.updateSupported("447.0.0.55.81", "447.0.0.55.81"))
        assertFalse(GuardPolicy.updateSupported("2026.43.1", "2026.41.0"))
    }
}
