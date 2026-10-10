plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Use the Android Gradle Plugin and Kotlin versions of the NexAlloy release you target.
    implementation("com.android.tools.build:gradle:9.2.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.2.10")
}
