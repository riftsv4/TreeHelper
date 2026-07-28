plugins {
    id("net.fabricmc.fabric-loom") version ("1.16.1")
    id("maven-publish")
}

// Jar: treehelper-<mod version>-<mc version>.jar
version = "${project.property("mod_version")}-${project.property("minecraft_version")}"
group = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
}

// MC 26.1.2 targets Java 25. Select the JDK via Gradle toolchain (compile + loom forks).
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    mavenLocal()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.neoforged.net/releases/") }
    maven { url = uri("https://maven.parchmentmc.org") }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:${project.property("minecraft_version")}")
    // Deobf 26.x jar: no mappings, plain implementation (no remapping needed) — matches Sodium/CoalFlipper.
    "implementation"("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    "implementation"("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

java {
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
