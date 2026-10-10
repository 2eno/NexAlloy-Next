package io.github.nexalloy.twoeno

import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.PatchBundle
import io.github.nexalloy.twoeno.interpals.InterPalsPatches
import io.github.nexalloy.twoeno.kleinanzeigen.KleinanzeigenPatches
import io.github.nexalloy.twoeno.spotify.SpotifyPatches
import io.github.nexalloy.twoeno.untappd.UntappdPatches

/** Entry point of the 2eno Patches bundle, named in its manifest. */
object TwoenoPatches : PatchBundle {
    override val apps = listOf(
        AppPatchInfo("Spotify", "com.spotify.music", SpotifyPatches),
        AppPatchInfo("Kleinanzeigen", "com.ebay.kleinanzeigen", KleinanzeigenPatches),
        AppPatchInfo("Untappd", "com.untappdllc.app", UntappdPatches),
        AppPatchInfo("InterPals", "net.interpals", InterPalsPatches),
    )
}
