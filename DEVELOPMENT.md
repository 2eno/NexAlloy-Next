# Development Guide

## Architecture

NexAlloy is split into a module and patch bundles, like Morphe Manager and Morphe patch sources. This repository
holds the module; the bundles NexAlloy presets live in [NexAlloyNext-Patches](https://github.com/2eno/NexAlloyNext-Patches).

```text
📦 NexAlloyNext
 ├ app/            The LSPosed module: loads bundles into apps, manages patch sources (the "manager")
 ├ patcher/        The patcher API bundles compile against: patch(), fingerprints, hook helpers
 ├ build-logic/    The nexalloy.bundle Gradle plugin that builds a bundle
 └ template/       Standalone template for patch bundles in other repositories
```

### Patch bundles

A bundle is an APK with the extension `.nxb`, built by the `nexalloy.bundle` plugin. Its manifest declares:

| `<meta-data>` | |
| --- | --- |
| `nexalloy.bundle.entry` | Class implementing [`PatchBundle`](patcher/src/main/java/io/github/nexalloy/PatchBundle.kt), a Kotlin `object` or a class with a no-arg constructor |
| `nexalloy.bundle.apiVersion` | `PATCHER_API_VERSION` the bundle was compiled against |
| `nexalloy.bundle.author`, `nexalloy.bundle.description` | Shown in the manager |

The package name is the bundle id, the application label its name and `versionName` its version.

### How a bundle gets into an app

1. The manager downloads the `.nxb` from a [patch source](app/src/main/java/io/github/nexalloy/manager/PatchSource.kt),
   loads it once to list its apps and patches, and stores it as a *remote file* of the Xposed service
   ([BundleManager.kt](app/src/main/java/io/github/nexalloy/manager/BundleManager.kt)). The list of installed bundles
   and the apps they patch is kept in the `bundles` remote preferences, and LSPosed is asked to add the apps to the
   module scope.
2. When a scoped app starts, [MainHook.kt](app/src/main/java/io/github/nexalloy/MainHook.kt) reads that list, copies
   the bundles for the app from the remote files into the app's code cache, and loads them with a `DexClassLoader`
   whose parent is the module's class loader.
   Frameworks like LSPosed and Vector rename the Xposed API packages and rewrite only the module's own code. NexAlloy
   detects the renaming from its own classes and rewrites the bundle classes that use the API into a separate dex
   ([XposedDexRemapper.kt](app/src/main/java/io/github/nexalloy/core/XposedDexRemapper.kt)), cached per renaming.
   The bundle's class loader only sees the module's shared packages (patcher API, DexKit, Kotlin stdlib, Xposed APIs).
3. Each bundle's resources are registered with `BundleResources`, then
   [PatchSession](patcher/src/main/java/io/github/nexalloy/PatchSession.kt) runs the enabled patches of each bundle
   with its own DexKit cache.

Because the module's class loader is the parent, **classes the module ships always win over a bundle's copy**.
The module therefore keeps the whole patcher API, DexKit and the Kotlin stdlib (see
[proguard-rules.pro](app/proguard-rules.pro)), bundles exclude them, and a bundle must not declare classes in the
module's packages (`io.github.nexalloy`, `io.github.nexalloy.morphe` and the module's own subpackages).

The APK's [scope.list](app/src/main/resources/META-INF/xposed/scope.list) only requests NexAlloy itself. The apps a
bundle patches are requested when the bundle is installed (installed apps only, see `BundleManager.missingScope`).

Patch toggles are stored in the per-app remote preferences under `<bundle id>/<patch name>`.

## Writing a patch bundle

Copy the [template](template) into a new repository. [NexAlloyNext-Patches](https://github.com/2eno/NexAlloyNext-Patches)
is a larger example with two bundles, shared extension code and fingerprint tests.

### Bundle module

```kotlin
plugins {
    id("nexalloy.bundle")
}

android {
    namespace = "com.example.patches"
    defaultConfig {
        applicationId = "com.example.patches" // the bundle id
        versionCode = 1
        versionName = "1.0.0"
    }
}

nexalloyBundle {
    entry = "com.example.patches.ExamplePatches"
    author = "Your name"
    descriptionResource = "bundle_description"
    resourcePackageId = 0x60 // unique among bundles installed together
}

dependencies {
    compileOnly("io.github.nexalloy:patcher:1.0.0")
}
```

`./gradlew :bundle:assembleBundle` writes `build/outputs/bundle/<module>-<version>.nxb`. Publish it as a release
asset, and users can add the repository as a patch source.

The patcher API is published to the Maven repository `https://raw.githubusercontent.com/2eno/NexAlloyNext/maven`
by the release workflow. Add it and `https://api.xposed.info` to your repositories.

Rules for bundles:

- Depend on the patcher with `compileOnly`; NexAlloy provides it, DexKit, the Xposed APIs and the Kotlin stdlib at runtime.
  Compile with a Kotlin version not newer than NexAlloy's.
- Keep the bundle's resources in its own package id (`resourcePackageId`). Patches make them available to the app
  with `appContext.addModuleAssets()`. `R` belongs to the bundle's namespace.
- Layouts inflated by the app that reference bundle classes need the bundle's class loader in the app's chain:
  `injectSelfClassLoaderToHost(bundle.classLoader, classLoader, "your.package")`.
- Use `bundle.classLoader`, not `this::class.java.classLoader` (that is the module's), when a patch needs its own class loader.

### Entry point

```kotlin
object ExamplePatches : PatchBundle {
    override val apps = listOf(
        AppPatchInfo("Contoso", "com.contoso.app", arrayOf(UnlockPro)),
    )
}
```

### Recommended app versions

Like Morphe's `compatibleWith`, a bundle can tell which app versions its patches were tested with, newest first.
NexAlloyNext recommends the newest version that is not experimental and shows how the installed version compares.
Leave `targets` empty if the patches find everything by fingerprints and work with any version (patcher API 2).

```kotlin
AppPatchInfo(
    "YouTube", "com.google.android.youtube", YouTubePatches,
    targets = listOf(AppTarget("21.39.522", experimental = true), AppTarget("21.16.256")),
)
```

### Patches

Patches follow this structure:

```text
📦your.patches.app.category
 ├ Fingerprints.kt
 └ SomePatch.kt
```

#### `Fingerprints.kt`
```kotlin
package com.example.patches.contoso.misc.unlock.plus

import io.github.nexalloy.morphe.Fingerprint

object IsPlusUnlockedFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("genius"),
)
```

#### `UnlockPlusPatch.kt`
```kotlin
package com.example.patches.contoso.misc.unlock.plus

import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import io.github.nexalloy.patch

val UnlockPlus = patch(name = "Unlock Plus") {
    IsPlusUnlockedFingerprint.hookMethod(returnConstant(true))
}
```

Patches named `""` or starting with `<` are internal: they always run and are hidden in the manager.

### Porting Upstream Patches

The [FingerprintCompat.kt](patcher/src/main/java/io/github/nexalloy/morphe/FingerprintCompat.kt) utility assists in translating ReVanced Patcher API calls to DexKit Matchers. While simple fingerprints can often be directly copied, consider the following translation patterns for complex cases:

1.  **Literal Mapping:**
    If an upstream patch defines a literal within the patch file (e.g., `aLiteral = resourceMappings["id", "aLiteral"]`) for use in a fingerprint, convert it to a getter property in the corresponding `Fingerprints.kt` file.

    *From (Upstream):*
    ```kotlin
    // In ***Patch.kt beside the Fingerprints.kt
    // val aLiteral = resourceMappings["id", "aLiteral"]
    fingerprint { literal { aLiteral } }
    ```

    *To (This Project's Fingerprints.kt):*
    ```kotlin
    val aLiteral get() = resourceMappings["id", "aLiteral"] // Defined in Fingerprints.kt
    fingerprint { literal { aLiteral } }
    ```

2.  **Custom Class and Method Matchers:**

    *From (Upstream):*
    ```kotlin
    fingerprint {
        custom { method, classDef ->
            method.name == "onCreate" && classDef.endsWith("/MusicActivity;")
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    fingerprint {
        methodMatcher { name = "onCreate" }
        classMatcher { className(".MusicActivity", StringMatchType.EndsWith) }
    }
    ```

3.  **Instruction-based Method Reference:**

    *From (Upstream):*
    ```kotlin
    fun indexOfTranslationInstruction(method: Method) =
        method.indexOfFirstInstructionReversed {
            getReference<MethodReference>()?.name == "setTranslationY"
        }

    val motionEventFingerprint = fingerprint {
        custom { method, _ ->
            indexOfTranslationInstruction(method) >= 0
        }
    }
    ```

    *To (This Project):*
    ```kotlin
    val motionEventFingerprint = fingerprint {
        methodMatcher { addInvoke { name = "setTranslationY" } }
    }
    ```

4.  **Matching Specific Class Types or Defining Classes:**
    When an upstream `custom` block primarily checks `classDef.type` or `method.definingClass`, this translates to a `classMatcher`:

    ```kotlin
    fingerprint {
        classMatcher { descriptor = "Lcom/example/SomeClass;" }
        methodMatcher { name = "targetMethod" }
    }
    ```

5.  **Porting Complex `custom` Logic or Chained Lookups with Direct Finders:**
    For intricate upstream fingerprints, use the direct finder functions (e.g., `findMethodDirect`, `findClassDirect`) from `FingerprintCompat.kt`, combining DexKit's filtering with Kotlin's collection processing:

    ```kotlin
    val complexCustomFingerprint = findMethodDirect {
        findMethod {
            matcher {
                returns("Lcom/example/ReturnType;")
                declaredClass { descriptor = "Lcom/example/HostClass;" }
            }
        }
        .filter { methodData -> methodData.name.startsWith("get") }
        .single { methodData ->
            methodData.paramTypes.size == 1 &&
            methodData.paramTypes.firstOrNull()?.descriptor == "Lcom/example/ParameterType;"
        }
    }
    ```

## Releasing

Push a tag `X.Y.Z` (Z is the module's `versionCode`), or `X.Y.Z-suffix` for a pre-release, or run the Release workflow
with a tag name. It attaches the module APK to a GitHub release and publishes the patcher API to the `maven` branch.
Without signing secrets, pre-releases are signed with a throwaway debug key. Bump `PATCHER_API_VERSION` (with
`patcherApiVersion` in `gradle.properties`) and `patcherVersion` when the patcher API gains something bundles may use.
