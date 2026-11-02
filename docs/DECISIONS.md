# Architectural and Design Decisions

## 1. Pre-release Schema Migration for UUID Format (Item 0)

### Context
Earlier local pre-release runs of Chronos generated task UUIDs using `Uuid.random().toHexString()` (32-character hex without dashes), which caused runtime failures on Android when parsed with `java.util.UUID.fromString`. The default ID generator has been updated to use `Uuid.random().toString()` (standard 36-character canonical dashed UUID).

### Decision
Pre-release Room schemas and local databases are not migrated for dashless UUIDs. Since this only affects local developer devices prior to the `0.1.0-alpha` release, developers should clear application data or uninstall previous builds.
