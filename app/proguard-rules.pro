# Patch bundles are loaded at runtime with the module's ClassLoader as parent and link against
# the patcher API and everything it exposes. Keep all of it intact, including the Kotlin stdlib.
-dontobfuscate
-dontoptimize
-keep class io.github.nexalloy.** { *; }
-keep class kotlin.** { *; }
-keep class org.luckypray.dexkit.** { *; }
-keep class com.google.flatbuffers.** { *; }
-keep class androidx.annotation.** { *; }

-dontwarn io.github.libxposed.annotation.**
-dontwarn de.robv.android.xposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
