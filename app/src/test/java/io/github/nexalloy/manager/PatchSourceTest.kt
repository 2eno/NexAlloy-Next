package io.github.nexalloy.manager

import io.github.nexalloy.updates.AppUpdateChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatchSourceTest {
    private fun repo(url: String) = PatchSource.create("test", url).githubRepository

    @Test
    fun githubRepository() {
        assertEquals("2eno/NexAlloyNext", repo("2eno/NexAlloyNext"))
        assertEquals("2eno/NexAlloyNext", repo("https://github.com/2eno/NexAlloyNext"))
        assertEquals("2eno/NexAlloyNext", repo("https://github.com/2eno/NexAlloyNext/"))
        assertEquals("2eno/NexAlloyNext", repo(" https://github.com/2eno/NexAlloyNext.git "))
        assertEquals("some.org/patches_x", repo("some.org/patches_x"))
    }

    @Test
    fun jsonSourceIsNotARepository() {
        assertNull(repo("https://example.com/patches-bundle.json"))
        assertNull(repo("https://github.com/2eno/NexAlloyNext/releases/latest"))
    }

    @Test
    fun versionCodeOfTag() {
        assertEquals(12, AppUpdateChecker.versionCodeOf("3.0.12"))
        assertEquals(6, AppUpdateChecker.versionCodeOf("3.0.6-alpha"))
        assertEquals(7, AppUpdateChecker.versionCodeOf("v3.0.7"))
        assertEquals(110, AppUpdateChecker.versionCodeOf("110-2.0.110"))
        assertEquals(0, AppUpdateChecker.versionCodeOf("autobuild"))
    }
}
