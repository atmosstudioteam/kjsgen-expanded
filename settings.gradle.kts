pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.10.+"
    id("dev.kikugie.stonecutter") version "0.9.+"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (it in loaders) version("$version-$it", version)
        }

        // Target matrix is being brought up incrementally (NeoForge 1.21.1 first).
        // Fabric 1.21.1 and the 1.20.1 / 1.19.2 (forge/fabric) targets are added
        // in later phases once the loader abstraction is in place:
        //   mc("1.19.2", "forge", "fabric")
        //   mc("1.20.1", "forge", "fabric")
        mc("1.21.1", "neoforge")

        vcsVersion = "1.21.1-neoforge"
    }
    create(rootProject)
}

rootProject.name = "kjsgen"
