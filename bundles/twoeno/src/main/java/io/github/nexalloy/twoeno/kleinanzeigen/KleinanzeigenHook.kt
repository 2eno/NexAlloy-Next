package io.github.nexalloy.twoeno.kleinanzeigen

import io.github.nexalloy.twoeno.kleinanzeigen.ads.HideAds
import io.github.nexalloy.twoeno.kleinanzeigen.privacy.SanitizeSharingLinks
import io.github.nexalloy.twoeno.kleinanzeigen.pur.HidePur

val KleinanzeigenPatches = arrayOf(HideAds, HidePur, SanitizeSharingLinks)
