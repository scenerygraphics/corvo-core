pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }

    repositories {
        gradlePluginPortal()
    }
}


rootProject.name = "corvo"
//includeBuild("../scenery")

gradle.rootProject {
    group = "graphics.scenery"
    version = "0.1"
    description = "VR visualization of dimensionally reduced single cell transcriptomics data"
}