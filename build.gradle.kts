plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
    kotlin("jvm")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com/")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.103.0+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.10+kotlin.2.3.20")
    modCompileOnly("com.terraformersmc:modmenu:11.0.1")
    modLocalRuntime("com.terraformersmc:modmenu:11.0.1")

    // JediTerm & PTY support - Using standard Fabric 'include'
    // We must include all transitive dependencies manually
    fun bundled(dep: String) {
        modImplementation(dep) {
            exclude(group = "org.jetbrains.kotlin")
            exclude(group = "org.jetbrains", module = "annotations")
            exclude(group = "org.slf4j")
        }
        include(dep)
    }

    bundled("org.jetbrains.jediterm:jediterm-core:3.50")
    bundled("org.jetbrains.jediterm:jediterm-ui:3.50")
    bundled("org.jetbrains.pty4j:pty4j:0.12.13")
    bundled("org.jetbrains.pty4j:purejavacomm:0.0.11.1")
    
    // JNA is often provided by the loader, but we include it for pty4j stability
    // Note: modImplementation of JNA might be tricky if it's not a mod, 
    // so we use implementation + include
    implementation("net.java.dev.jna:jna:5.12.1")
    include("net.java.dev.jna:jna:5.12.1")
    implementation("net.java.dev.jna:jna-platform:5.12.1")
    include("net.java.dev.jna:jna-platform:5.12.1")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_version" to project.property("minecraft_version"),
            )
        )
    }
}

java {
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
kotlin {
    jvmToolchain(21)
}
