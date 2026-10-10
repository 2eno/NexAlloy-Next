pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        flatDir {
            dirs(rootDir.resolve("libs"))
        }
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
    }
}

plugins {
    id("com.android.settings") version ("9.2.1")
}

android {
    compileSdk = 37
    minSdk = 27
}

rootProject.name = "NexAlloy-Next"

// The module: loads patch bundles into apps and manages patch sources.
include(":app")
// The API patch bundles are compiled against.
include(":patcher")

// Patch bundles released alongside the module.
include(":stub")
include(":bundles:nexalloy")
include(":bundles:twoeno")
