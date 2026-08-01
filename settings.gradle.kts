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
        // On 1.20.1 KubeJS ships BOTH loaders (KubeJS 6 / 2001.x has forge + fabric builds), so 1.20.1
        // carries both. Note 1.20.1 uses Forge, not NeoForge: KubeJS/JEI have no NeoForge 1.20.1 build
        // (KubeJS's NeoForge support starts at 1.20.4), same KubeJS-native-loader criterion throughout.
        //   mc("1.19.2", "forge", "fabric")   // next phase
        mc("1.20.1", "forge", "fabric")
        mc("1.21.1", "neoforge")

        vcsVersion = "1.21.1-neoforge"
    }
    create(rootProject)
}

rootProject.name = "kjsgen"
