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


