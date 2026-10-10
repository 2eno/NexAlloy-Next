package com.example.patches

import com.example.patches.misc.UnlockPro
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.PatchBundle

/** Entry point of the bundle, named by `nexalloyBundle.entry`. */
object ExamplePatches : PatchBundle {
    override val apps = listOf(
        AppPatchInfo("Contoso", "com.contoso.app", arrayOf(UnlockPro)),
    )
}
