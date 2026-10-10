import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    `maven-publish`
}

group = "io.github.nexalloy"
version = providers.gradleProperty("patcherVersion").get()

android {
    namespace = "io.github.nexalloy.patcher"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-call-assertions",
            "-Xcontext-parameters"
        )
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // DexKit fork with instruction operand introspection, published alongside the patcher as `dexkit`.
    // https://github.com/NexAlloy/DexKit/commit/59d5d332a1ac1bc543d1588b9c9f0377ea3c6ea5
    api(":dexkit-android@aar")
    api(libs.flatbuffers) // dexkit dependency
    api(libs.annotation)
    // Provided by the Xposed framework at runtime.
    api(libs.xposed)
    api(libs.libxposed.api)
}

// The Gradle module metadata would reference the flatDir DexKit; consumers use the fixed POM instead.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

tasks.withType<GenerateMavenPom>().configureEach {
    notCompatibleWithConfigurationCache("The POMs are rewritten by Kotlin script lambdas")
}

val publicationGroup = group.toString()
val publicationVersion = version.toString()
val flatbuffersVersion = libs.versions.flatbuffers.get()

publishing {
    repositories {
        // Published by the release workflow to the `maven` branch, served as
        // https://raw.githubusercontent.com/2eno/NexAlloy-Next/maven
        maven {
            name = "release"
            url = uri(rootProject.layout.buildDirectory.dir("maven"))
        }
    }
    publications {
        register<MavenPublication>("release") {
            artifactId = "patcher"
            afterEvaluate { from(components["release"]) }
            pom.withXml {
                // Point the flatDir DexKit dependency at the `dexkit` publication below.
                val dependencies = (asNode().get("dependencies") as groovy.util.NodeList)[0] as groovy.util.Node
                dependencies.children().map { it as groovy.util.Node }
                    .filter { (it.get("artifactId") as groovy.util.NodeList).text() == "dexkit-android" }
                    .forEach { dependencies.remove(it) }
                dependencies.appendNode("dependency").apply {
                    appendNode("groupId", publicationGroup)
                    appendNode("artifactId", "dexkit")
                    appendNode("version", publicationVersion)
                    appendNode("type", "aar")
                    appendNode("scope", "compile")
                }
            }
        }
        register<MavenPublication>("dexkit") {
            artifactId = "dexkit"
            artifact(rootProject.file("libs/dexkit-android.aar"))
            artifact(rootProject.file("libs/dexkit-android-sources.jar")) { classifier = "sources" }
            pom {
                packaging = "aar"
                withXml {
                    asNode().appendNode("dependencies").appendNode("dependency").apply {
                        appendNode("groupId", "com.google.flatbuffers")
                        appendNode("artifactId", "flatbuffers-java")
                        appendNode("version", flatbuffersVersion)
                        appendNode("scope", "runtime")
                    }
                }
            }
        }
    }
}
