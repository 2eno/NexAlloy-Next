package io.github.nexalloy.manager

import io.github.nexalloy.common.VersionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatchSourceTest {
    private fun repo(url: String) = PatchSource.create("test", url).githubRepository

    @Test
    fun githubRepository() {
        assertEquals("2eno/NexAlloy-Next", repo("2eno/NexAlloy-Next"))
        assertEquals("2eno/NexAlloy-Next", repo("https://github.com/2eno/NexAlloy-Next"))
        assertEquals("2eno/NexAlloy-Next", repo("https://github.com/2eno/NexAlloy-Next/"))
        assertEquals("2eno/NexAlloy-Next", repo(" https://github.com/2eno/NexAlloy-Next.git "))
        assertEquals("some.org/patches_x", repo("some.org/patches_x"))
    }

    @Test
    fun jsonSourceIsNotARepository() {
        assertNull(repo("https://example.com/patches-bundle.json"))
        assertNull(repo("https://github.com/2eno/NexAlloy-Next/releases/latest"))
    }

    @Test
    fun versionInfoFromTagName() {
        assertEquals(VersionInfo(12, "3.0.12"), VersionInfo.fromTagName("3.0.12"))
        assertEquals(VersionInfo(110, "2.0.110"), VersionInfo.fromTagName("110-2.0.110"))
    }
}
