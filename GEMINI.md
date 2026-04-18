# Paper Engine Editor - Project Context

## Project Overview
Paper Engine Editor is a Minecraft mod developed using the **Fabric** loader and **Kotlin**. It is intended to serve as an editor for the "Paper Engine" system.

### Core Technologies
- **Language:** Kotlin (2.0.21)
- **Mod Loader:** Fabric
- **Minecraft Version:** 1.21.1
- **Java Version:** 21
- **Build System:** Gradle (Kotlin DSL)

### Architecture
The project follows a standard Fabric mod structure:
- `src/main/kotlin`: Source code (namespace: `work.stdpi.pge.editor`)
- `src/main/resources`: Mod metadata (`fabric.mod.json`), mixins, and assets.

## Building and Running
The project uses the Gradle wrapper for all tasks.

- **Build Mod:** `./gradlew build` (Output in `build/libs/`)
- **Run Minecraft Client:** `./gradlew runClient`
- **Run Minecraft Server:** `./gradlew runServer`
- **Clean Build:** `./gradlew clean`

## Development Conventions
- **Naming:** Follows standard Kotlin and Fabric conventions.
- **Mod ID:** `pge-editor`
- **Namespace:** `work.stdpi.pge.editor`
- **Mixins:** Configured in `pge-editor.mixins.json` and located in `work.stdpi.pge.editor.mixin`.
- **Resources:** Assets should be placed in `src/main/resources/assets/pge-editor/`.
