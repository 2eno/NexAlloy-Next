package com.example.patches.misc

import io.github.nexalloy.morphe.Fingerprint

/** Matches `boolean isPro()`, the only method returning a boolean that uses the string "pro_status". */
object IsProFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("pro_status"),
)
