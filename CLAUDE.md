# Chronos Project Manual

## Project Overview
Chronos is a Kotlin Multiplatform (KMP) persistence-backed background task scheduling library for Android, iOS, and JVM Desktop.

## Project Structure
- `chronos-core/`: Core library (KMP)
- `chronos-processor/`: Custom KSP annotation processor (JVM only)
- `chronos-testing/`: Test harness (FakeClock, TestChronos)
- `sample/`: Compose Multiplatform sample app

## Documentation & Ground Truth
All design documents, technical plans, and architecture details are stored under `docs/`:
- `docs/project_plan.md`: Phases, API design, and schema details.
- `docs/SYSTEM_DESIGN_HLD_DFD.md`: HLD, Level 0 & 1 DFDs.
- `docs/HLD_DEEP_DIVES.md`: Subsystem deep-dives (iOS BGTaskScheduler, WorkChain DAG, etc.).
- `docs/LLD.md`: Low-Level Design (class contracts, Room 3 schemas, execution algorithms).
- `docs/VISION_AND_EXECUTION.md`: Product vision and Spike details.

## Build & Validation Commands
- Run help / validate configuration: `./gradlew help`
- Build project: `./gradlew build`
- Run KSP codegen & compile: `./gradlew compileKotlin`

## Developer & Agent Guidelines
1. **Always refer to `docs/`** as the single source of truth. Do not search old brain directories.
2. **Version Catalog**: Keep all versions in `gradle/libs.versions.toml`.
3. **Gradle Properties**: Keep optimizations in `gradle.properties`.
4. **Kotlin DSL**: Use dot notation for catalog references (e.g. `libs.workmanager.ktx` instead of `-ktx`).

## Shared Context Synchronization
When user asks to sync or point to the shared brain context, the agent must read from and write to (both markdown and metadata JSON) the directory:
`/Users/sahil.makkar@grofers.com/.gemini/antigravity-cli/brain/ded15d14-3fea-4e72-8cd7-e0707e46024e/`

## Custom `/proto` Commands
Support the following custom prefix commands by reading/writing to `/Users/sahil.makkar@grofers.com/.proto_registry.json`:
- `/proto register <name> [path]`: Map `<name>` to the specified `<path>` (which acts as a shared brain folder) in the registry JSON. If `<path>` is omitted, resolve the current active session's brain directory path and use that.
- `/proto list` / `/proto open`: List all registered projects/shared brains.
- `/proto sync <name>`: Find the path for `<name>` in the registry, and copy all `.md` and `.metadata.json` files from that path to the current project's `docs/` folder. Also update `CLAUDE.md` and the sync script to point to it.
