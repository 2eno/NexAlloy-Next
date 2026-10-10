import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
}

fun gitOutput(vararg args: String) = providers.exec {
    commandLine("git", *args)
    workingDir = rootProject.rootDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }

val gitCommitHashProvider = gitOutput("rev-parse", "--short", "HEAD").map { it.ifEmpty { "unknown" } }
val gitCommitDateProvider = gitOutput("log", "-1", "--format=%ct").map { it.ifEmpty { "0" } }

android {
    namespace = "io.github.nexalloy.core"

    defaultConfig {
        applicationId = "io.github.nexalloy.next"
        versionCode = 1
        versionName = "3.0.$versionCode"
        buildConfigField("String", "COMMIT_HASH", "\"${gitCommitHashProvider.get()}\"")
        buildConfigField("long", "COMMIT_DATE", "${gitCommitDateProvider.get()}L")
        // GitHub repository checked for updates and used as the default patch source.
        // Builds by GitHub Actions of a fork use the fork's releases.
        val updateRepository = providers.environmentVariable("GITHUB_REPOSITORY").getOrElse("2eno/NexAlloy-Next")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
    }
    val ksFile = rootProject.file("signing.properties")
    signingConfigs {
        if (ksFile.exists()) {
            create("release") {
                val properties = Properties().apply {
                    ksFile.inputStream().use { load(it) }
                }

                storePassword = properties["KEYSTORE_PASSWORD"] as String
                keyAlias = properties["KEYSTORE_ALIAS"] as String
                keyPassword = properties["KEYSTORE_ALIAS_PASSWORD"] as String
                storeFile = file(properties["KEYSTORE_FILE"] as String)
            }
        }
    }
    buildFeatures.buildConfig = true
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "META-INF/*.version"
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(project(":patcher")) {
        // Provided by the Xposed framework.
        exclude(group = "de.robv.android.xposed")
        exclude(group = "io.github.libxposed", module = "api")
    }
    compileOnly(libs.xposed)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}
