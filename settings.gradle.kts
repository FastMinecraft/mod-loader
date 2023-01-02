rootProject.name = "mod-loader"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fastmc.dev/")
    }

    val kotlinVersion: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm").version(kotlinVersion)
    }
}

include("runtime", "plugin")