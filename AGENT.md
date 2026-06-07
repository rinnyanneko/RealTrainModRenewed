# Agent Instructions

## Repository Snapshot

- This is `RealTrainModRenewed`, a Minecraft 1.21.1 NeoForge rewrite/renewal of the original RTMU codebase.
- The main mod id is `realtrainmodrenewed`; the main package is `cc.mirukuneko.realtrainmodrenewed`.
- The build is Gradle Groovy DSL with Java 21 toolchains and NeoGradle userdev.
- `atsa/` is a separate included Gradle subproject that builds the `atsassistmod` companion mod jar. It depends on the root RTMU project at compile time and should stay separated unless the user asks to merge it.
- The repo currently has about 223 Java source files and a large resource surface: model JSON, PNG textures, OGG sounds, JavaScript render/sound scripts, MQO/MQOZ models, and pack-import tooling.

## Important Paths

- `build.gradle`, `settings.gradle`, `gradle.properties`: root NeoForge build configuration.
- `src/main/java/com/portofino/realtrainmodunofficial/RealTrainModRenewed.java`: root mod entry point.
- `src/main/java/com/portofino/realtrainmodunofficial/`: main mod code.
- `src/main/resources/META-INF/neoforge.mods.toml`: root mod metadata.
- `src/main/resources/assets/`: bundled Minecraft/RTM/RTMU assets and WebCTC static files.
- `atsa/build.gradle`: ATSA companion mod build.
- `atsa/src/main/java/jp/kaiz/atsassistmod/ATSAssistMod.java`: ATSA mod entry point.
- `tools/`: Python and shell utilities for converting/importing model and vehicle packs.

## Development Direction

- The long-term direction is to rewrite the Java codebase in Kotlin and add new features.
- Prefer Kotlin for new gameplay, loader, data, UI, and feature code once the Kotlin Gradle plugin/source sets are introduced.
- Migrate incrementally. Keep public behavior, mod ids, registry names, packet ids, resource paths, and saved-data formats compatible unless the user explicitly requests a breaking change.
- Do not start a broad Java-to-Kotlin rewrite just because a file is touched. Convert only the smallest useful area for the requested work, or create Kotlin beside existing Java when that keeps risk lower.
- When converting Java to Kotlin, preserve NeoForge lifecycle semantics, event-bus registration timing, `DeferredRegister` behavior, sided client/server boundaries, and Java interop for the ATSA subproject.
- Avoid renaming assets or namespaces casually. A lot of compatibility depends on exact paths under `assets/minecraft`, `assets/rtm`, and `assets/realtrainmodunofficial`.

## Feature Work Guidance

- Check the relevant registry, network payload, block/entity/item, renderer, pack-loader, and resource files before adding a feature.
- For train, rail, vehicle, installed-object, signal, WebCTC, script, or ATSA features, follow the existing package boundary instead of adding cross-cutting code in the root mod class.
- Keep root entry-point changes minimal. Prefer small registration helpers and feature-local classes.
- Treat external model/vehicle/rail packs as compatibility-sensitive input. Use structured parsing and existing loaders rather than ad hoc string handling.
- Keep generated/build outputs out of source changes.

## Build And Verification

- Use `./gradlew build` on Unix-like shells or `.\gradlew.bat build` on Windows PowerShell for a full build.
- If touching only one subproject, still consider the root build because `atsa` compile-time depends on the root project.
- If Gradle needs network access for dependencies and the sandbox blocks it, ask for approval rather than working around the build.
- Report any build or test command that could not be run, including the exact reason.

## CodeRabbit Review Requirement

- After every code or documentation change, run a CodeRabbit review before reporting the work as complete.
- On Windows, run CodeRabbit from WSL. Use the available `coderabbit` command there rather than a native Windows shell invocation.
- Prefer `coderabbit review --agent -t uncommitted` for local changes.
- Include the CodeRabbit result, or clearly state why it could not be run, in the final response.
