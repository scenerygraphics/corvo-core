import org.gradle.internal.os.OperatingSystem.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    maven
    application
    kotlin("jvm") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "graphics.scenery"
version = "0.1.0-SNAPSHOT"

description = "xtra-dimension_vr"

repositories {
    mavenCentral()
//    google()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
    maven("https://nexus.senbox.net/nexus/content/groups/public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
//    maven("https://maven.mozilla.org/maven2")
    maven("https://dl.bintray.com/deepspeech-ci/org.deepspeech")
    maven("https://dl.bintray.com/alissy/org.mozilla.deepspeech")

    maven("https://alphacephei.com/maven/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("graphics.scenery:scenery:f11dc08b")  //e1e0b7e4fc stable
    implementation("org.junit.jupiter:junit-jupiter:5.4.2")
    val lwjglNative = "natives-" + when (current()) {
        WINDOWS -> "windows"
        LINUX -> "linux"
        else -> "macos"
    }

    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        implementation("org.lwjgl:lwjgl$it:3.2.3")
        if (it != "-vulkan")
            runtimeOnly("org.lwjgl", "lwjgl$it", version = "3.2.3", classifier = lwjglNative)
    }

    implementation("org.joml:joml:1.9.25")
    implementation("org.janelia.saalfeldlab:n5-imglib2:3.5.1")
    implementation("org.jogamp.gluegen:gluegen-rt:2.3.2")
    implementation("org.jogamp.jogl:jogl-all:2.3.2")
    implementation("org.scijava:ui-behaviour:2.0.3")
    implementation("graphics.scenery:spirvcrossj:0.7.1-1.1.106.0")
    implementation("org.nd4j:nd4j-api:1.0.0-beta7")
    implementation("org.nd4j:nd4j-native-platform:1.0.0-beta7")
    implementation("org.apache.commons:commons-math3:3.6.1")

    implementation("cisd:jhdf5:19.04.0") //hdf5 file reading

    implementation("edu.cmu.sphinx", "sphinx4-core", version = "5prealpha-SNAPSHOT")
    implementation("edu.cmu.sphinx", "sphinx4-data", version = "5prealpha-SNAPSHOT")

//    implementation("org.mozilla.deepspeech:libdeepspeech:0.9.3@aar")

    runtimeOnly("net.java.jinput", "jinput", version = "2.0.9", classifier = "natives-all")
    runtimeOnly("graphics.scenery", "spirvcrossj", version = "0.7.1-1.1.106.0", classifier = lwjglNative)

    // needed for logging to work correctly, don't use log4j, it's overkill in this case.
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")

    implementation("net.java.dev.jna", "jna", version = "5.7.0")
    implementation("com.alphacephei","vosk", version = "0.3.30+")
}

application {
//    mainClass.set("graphics.scenery.xtradimensionvr.XVisualizationKT")
    @Suppress("DEPRECATION")
    mainClassName = "graphics.scenery.xtradimensionvr.XVisualization"
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
        sourceCompatibility = "11"
    }

    named<Test>("test") {
        useJUnitPlatform()
    }

    shadowJar {
        isZip64 = true
    }

    jar {
        manifest {
            attributes("Class-Path" to "/libs/a.jar")
        }
    }
}