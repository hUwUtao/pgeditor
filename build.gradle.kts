plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.103.0+1.21.1")

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
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
