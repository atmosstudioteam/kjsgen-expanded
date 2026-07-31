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

        // Target matrix, brought up incrementally. 1.21.1 is NeoForge-only: KubeJS (the mod's whole
        // reason to exist) has no Fabric build for 1.21.1, so a 1.21.1-fabric target is pointless.
        // The cross-loader abstraction stays in the code for the older Fabric targets, where KubeJS
        // does exist, added in later phases:
        //   mc("1.19.2", "forge", "fabric")
        //   mc("1.20.1", "forge", "fabric")   // fabric side deferred
        // 1.20.1 targets Forge (not NeoForge): KubeJS/JEI only ship Forge builds for 1.20.1 —
        // KubeJS's NeoForge support starts at 1.20.4 — so Forge is the KubeJS-native loader here,
        // same criterion that made 1.21.1 NeoForge-only.
        mc("1.20.1", "forge")
        mc("1.21.1", "neoforge")

        vcsVersion = "1.21.1-neoforge"
    }
    create(rootProject)
}

rootProject.name = "kjsgen"
