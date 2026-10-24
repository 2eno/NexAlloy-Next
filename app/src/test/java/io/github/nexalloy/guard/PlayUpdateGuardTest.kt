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
}
