import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    runDirectory = rootProject.layout.projectDirectory.dir("run")
    clientOptions {
        narrator = false
    }
}

repositories {
    mavenCentral()                                 // JUnit, gson, other plain libs
    maven("https://maven.blamejared.com/")        // JEI
    maven("https://modmaven.dev")                  // JEI mirror + Mekanism
    maven("https://maven.latvian.dev/releases")    // KubeJS + addons
    maven("https://maven.createmod.net")           // Create / Ponder / Flywheel
    maven("https://maven.ithundxr.dev/snapshots")  // Registrate (Create dep)
    maven("https://jitpack.io")
}

val mc = property("minecraft_version") as String

dependencies {
    // Architectury API — the cross-loader abstraction (events, networking, keybinds, Platform).
    // Its artifact is loader-specific, so pick the matching one for the active chunk.
    if (mod.isNeoforge) {
        modImplementation("dev.architectury:architectury-neoforge:${property("architectury_version")}")
    }
    if (mod.isFabric) {
        modImplementation("dev.architectury:architectury-fabric:${property("architectury_version")}")
    }
    if (mod.isForge) {
        modImplementation("dev.architectury:architectury-forge:${property("architectury_version")}")
    }

    if (mod.isFabric) {
        // Fabric API — required at runtime; Architectury builds its Fabric implementation on top of it.
        modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

        // JEI (same version string as neoforge): API on the compile classpath, full jar in dev runtime.
        modCompileOnly("mezz.jei:jei-$mc-fabric-api:${property("jei_version")}")
        modLocalRuntime("mezz.jei:jei-$mc-fabric:${property("jei_version")}")

        // KubeJS, Mekanism and Create have no usable Fabric 1.21.1 build, which is why 1.21.1 ships
        // NeoForge-only. This block stays for the older Fabric targets (1.20.1 / 1.19.2), where those
        // mods do exist, so their dev-runtime dependencies get added here per version.
    }

    if (mod.isForge) {
        // 1.20.1-forge (Milestone A): core editor + KubeJS export. JEI is branched for JEI 15;
        // Mekanism (10.4 API) and Create dev-runtime are Milestone B follow-ups.
        // JEI: the forge jar carries the api classes, so compile against it directly; full jar in runtime.
        modCompileOnly("mezz.jei:jei-$mc-forge:${property("jei_version")}")
        modLocalRuntime("mezz.jei:jei-$mc-forge:${property("jei_version")}")

        // KubeJS 6 + its Rhino script engine (both Forge mods), dev-runtime only so runClient can
        // load exported scripts. KubeJS 6 has no tiny-java-server dep, so no forgeRuntimeLibrary here.
        modLocalRuntime("dev.latvian.mods:kubejs-forge:${property("kubejs_version")}")
        modLocalRuntime("dev.latvian.mods:rhino-forge:${property("rhino_version")}")
        // MixinExtras common classes (MixinExtrasBootstrap) must be on the classpath for the early
        // mixin bootstrap; Loom drops this non-mod transitive of KubeJS, so add it via the same
        // forgeRuntimeLibrary config that fixed KubeJS's runtime libs on 1.21.1-neoforge.
        forgeRuntimeLibrary("io.github.llamalad7:mixinextras-common:${property("mixinextras_version")}")
    }

    if (mod.isNeoforge) {
        // JEI: API on the compile classpath, full jar only in the dev runtime.
        modCompileOnly("mezz.jei:jei-$mc-neoforge-api:${property("jei_version")}")
        modLocalRuntime("mezz.jei:jei-$mc-neoforge:${property("jei_version")}")

        // KubeJS: dev-runtime only, so runClient can actually load the exported scripts.
        modLocalRuntime("dev.latvian.mods:kubejs-neoforge:${property("kubejs_version")}")
        // KubeJS needs these non-mod libraries at runtime, but FML does not discover non-mod jars on
        // its own (NeoForge <= 1.21.8), and Loom drops the runtime-scope transitives of a mod dependency.
        // The Architectury Loom config for "a runtime library FML must be told about" is
        // forgeRuntimeLibrary (shared by Forge and NeoForge) — plain runtimeOnly/localRuntime does NOT
        // reach the dev runtime classpath here. Without this KubeJSClient crashes with
        // NoClassDefFoundError: dev.latvian.apps.tinyserver...
        forgeRuntimeLibrary("dev.latvian.apps:tiny-java-server:${property("tiny_java_server_version")}")
        forgeRuntimeLibrary("com.github.rtyley:animated-gif-lib-for-java:${property("animated_gif_lib_version")}")
        // better-advanced-tooltips is an actual mod, so it stays on modLocalRuntime.
        modLocalRuntime("dev.latvian.mods:better-advanced-tooltips:${property("better_advanced_tooltips_version")}")

        // Mekanism: API at compile time (chemical registry / gas slots), full jar dev-runtime only.
        modCompileOnly("mekanism:Mekanism:${property("mekanism_version")}:api")
        modLocalRuntime("mekanism:Mekanism:${property("mekanism_version")}")
        modLocalRuntime("dev.latvian.mods:kubejs-mekanism-neoforge:${property("kubejs_mekanism_version")}")

        // Create + its runtime deps (slim jar excludes bundled libs), dev-runtime only.
        modLocalRuntime("com.simibubi.create:create-$mc:${property("create_version")}:slim") {
            isTransitive = false
        }
        modLocalRuntime("net.createmod.ponder:ponder-neoforge:${property("ponder_version")}+mc$mc")
        modLocalRuntime("dev.engine-room.flywheel:flywheel-neoforge-$mc:${property("flywheel_version")}")
        modLocalRuntime("com.tterrag.registrate:Registrate:${property("registrate_version")}")
        modLocalRuntime("dev.latvian.mods:kubejs-create-neoforge:${property("kubejs_create_version")}")
    }

    // Unit tests cover only pure, MC-independent core classes (e.g. UndoStack).
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Stonecraft's generatePackMCMetaJson writes into a generated resources dir the test source set also
// reads. Gradle 9's strict validation fails compileTestJava for that undeclared implicit dependency the
// first time the task actually executes (it's masked on chunks whose test output is already UP-TO-DATE).
// Declaring the dependency is the fix Gradle itself recommends. Uses matching{} so it's a no-op on any
// chunk where the metadata task is absent.
tasks.matching { it.name == "compileTestJava" }.configureEach {
    dependsOn(tasks.matching { it.name == "generatePackMCMetaJson" })
}
