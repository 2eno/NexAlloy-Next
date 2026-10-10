pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
        // The NexAlloy patcher API.
        maven(url = providers.gradleProperty("nexalloyMaven").get())
    }
}

rootProject.name = "my-nexalloy-patches"
include(":bundle")
