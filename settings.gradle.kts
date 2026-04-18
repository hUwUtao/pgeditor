pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "JetBrains"
            url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        }
    }
}

rootProject.name = "paper-engine-editor"
