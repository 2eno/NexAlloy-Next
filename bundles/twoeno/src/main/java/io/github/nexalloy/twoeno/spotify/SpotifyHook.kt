package io.github.nexalloy.twoeno.spotify

import io.github.nexalloy.twoeno.spotify.ads.BlockPopupAds
import io.github.nexalloy.twoeno.spotify.ads.HideAdSections
import io.github.nexalloy.twoeno.spotify.ads.HideAdViews
import io.github.nexalloy.twoeno.spotify.ads.HideContextMenuUpsells
import io.github.nexalloy.twoeno.spotify.ads.HidePlaylistAds
import io.github.nexalloy.twoeno.spotify.ads.HideVideoAds
import io.github.nexalloy.twoeno.spotify.ads.MuteAudioAds
import io.github.nexalloy.twoeno.spotify.layout.HidePremiumTab
import io.github.nexalloy.twoeno.spotify.misc.FixThirdPartyLaunchersWidgets
import io.github.nexalloy.twoeno.spotify.privacy.SanitizeSharingLinks
import io.github.nexalloy.twoeno.spotify.theme.MaterialYouTheme
import io.github.nexalloy.twoeno.spotify.theme.RoundedCorners

val SpotifyPatches = arrayOf(
    MuteAudioAds,
    HideAdSections,
    HideAdViews,
    HideContextMenuUpsells,
    BlockPopupAds,
    HidePlaylistAds,
    HideVideoAds,
    HidePremiumTab,
    FixThirdPartyLaunchersWidgets,
    SanitizeSharingLinks,
    MaterialYouTheme,
    RoundedCorners,
)
