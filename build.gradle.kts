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
    if (mod.isNeoforge) {
        // JEI: API on the compile classpath, full jar only in the dev runtime.
        modCompileOnly("mezz.jei:jei-$mc-neoforge-api:${property("jei_version")}")
        modLocalRuntime("mezz.jei:jei-$mc-neoforge:${property("jei_version")}")

        // KubeJS: dev-runtime only, so runClient can actually load the exported scripts.
        modLocalRuntime("dev.latvian.mods:kubejs-neoforge:${property("kubejs_version")}")

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
