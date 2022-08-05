import org.gradle.internal.os.OperatingSystem.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    java
    maven
    application
    kotlin("jvm") version "1.4.20"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "graphics.scenery"
version = "0.1.0-SNAPSHOT"

description = "corvo"

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
    maven("https://nexus.senbox.net/nexus/content/groups/public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://alphacephei.com/maven/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("graphics.scenery:scenery:f11dc08b")  //e1e0b7e4fc stable (f79cfd58ae) (instancing impr. f11dc08b)
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

    // needed for logging to work correctly, don't use log4j, it's overkill in this case.
    implementation("org.slf4j:slf4j-api:1.7.30")

    implementation("org.apache.commons:commons-math3:3.6.1")

    implementation("org.json:json:20210307")

    runtimeOnly("net.java.jinput", "jinput", version = "2.0.9", classifier = "natives-all")
    runtimeOnly("graphics.scenery", "spirvcrossj", version = "0.7.1-1.1.106.0", classifier = lwjglNative)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")

    api(githubRelease("JaneliaSciComp", "jhdf5", "jhdf5-19.04.1_fatjar", "sis-base-18.09.0.jar"))
    api(githubRelease("JaneliaSciComp", "jhdf5", "jhdf5-19.04.1_fatjar", "sis-jhdf5-1654327451.jar"))

    implementation("net.java.dev.jna", "jna", version = "5.7.0")
    implementation("com.alphacephei","vosk", version = "0.3.30+")

}

application {
//    mainClass.set("graphics.scenery.xtradimensionvr.XVisualizationKT")
    @Suppress("DEPRECATION")
    mainClassName ="graphics.scenery.corvo.XVisualization"

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

/**
 * Downloads a [file] from the given GitHub release, with [organization], [repository], and [release] tag given.
 */
fun githubRelease(organization: String, repository: String, release: String, file: String): ConfigurableFileCollection {
    val url = "https://github.com/$organization/$repository/releases/download/$release/$file"
    val output = File("$projectDir/external-libs/$organization-$repository-$release-$file")
    output.parentFile.mkdirs()

    if(!output.exists()) {
        val created = output.createNewFile()
        val stream = URL(url).openStream()
        val out = output.outputStream()
        stream.copyTo(out)
        out.close()
        stream.close()
    }
    return files(output.absolutePath)
}
