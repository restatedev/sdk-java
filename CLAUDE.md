# CLAUDE.md - Restate Java SDK

## Project Overview

This is the **Restate Java/Kotlin SDK** (`dev.restate`), a polyglot JVM SDK for building services on the [Restate](https://restate.dev) platform. It provides APIs for durable execution, virtual objects, workflows, and more.

## Build System

- **Gradle 9.3.0** with Kotlin DSL (`*.gradle.kts`)
- **Java 17** toolchain (minimum target)
- Dependencies managed via version catalog: `gradle/libs.versions.toml`
- Convention plugins in `buildSrc/src/main/kotlin/`

### Key Commands/

```bash
./gradlew build                    # Full build + tests
./gradlew test                     # Run tests only
./gradlew :sdk-core:test           # Run a single module's tests
./gradlew spotlessApply            # Auto-format all code
./gradlew spotlessCheck            # Check formatting (runs in CI)
./gradlew :sdk-aggregated-javadocs:javadoc  # Generate Java docs
./gradlew :dokkaHtmlMultiModule    # Generate Kotlin docs
```

### Before Submitting Changes

Always run `./gradlew spotlessApply` before committing — CI will fail on formatting violations.

## Code Style & Formatting

- **Java**: Google Java Format (enforced via Spotless)
- **Kotlin**: ktfmt (enforced via Spotless)
- **Gradle Kotlin DSL**: ktfmt (enforced via Spotless)
- **License headers**: Required on all `.java`, `.kt`, and `.proto` files (template in `config/license-header`)

Do NOT manually format code — let Spotless handle it.

## Project Structure

### Core Modules
- `sdk-common` — Shared interfaces and utilities (Java + Kotlin)
- `sdk-api` — Public Java API (`dev.restate.sdk`)
- `sdk-api-kotlin` — Public Kotlin API (`dev.restate.sdk.kotlin`)
- `sdk-core` — Core state machine implementation (shaded jar)
- `sdk-serde-jackson` — Jackson serialization support
- `sdk-serde-kotlinx` — Kotlinx serialization support

### Deployment Modules
- `sdk-http-vertx` — Vert.x HTTP server
- `sdk-lambda` — AWS Lambda integration
- `sdk-spring-boot` / `sdk-spring-boot-starter` — Spring Boot integration

### Code Generation
- `sdk-api-gen` — Java annotation processor (Handlebars templates)
- `sdk-api-kotlin-gen` — Kotlin Symbol Processing (KSP)
- `sdk-api-gen-common` — Shared codegen utilities
- Annotations: `@Service`, `@VirtualObject`, `@Workflow`

### Convenience Meta-Modules
- `sdk-java-http`, `sdk-java-lambda`, `sdk-kotlin-http`, `sdk-kotlin-lambda`

### Infrastructure
- `client` / `client-kotlin` — Admin clients
- `admin-client` — Admin API client
- `sdk-request-identity` — JWT/request signing
- `sdk-testing` — TestContainers-based test utilities

### Testing
- `test-services` — Test service implementations
- `sdk-fake-api` — Mock API for unit tests

## Testing

- **Framework**: JUnit 5 (Jupiter) + AssertJ
- **Integration tests**: Use TestContainers with Restate Docker image
- **CI matrix**: Java 17, 21, 25
- Tests live in `src/test/java` or `src/test/kotlin` within each module

## Key Conventions

- Package root: `dev.restate.sdk` (Java), `dev.restate.sdk.kotlin` (Kotlin)
- Java compiler flag `-parameters` is enabled for annotation processing
- Generated code goes to `build/generated/`
- Proto files in `src/main/service-protocol/`
- The `sdk-core` module uses Shadow plugin to shade dependencies

## Architecture Notes

- **`sdk-core`** is the most sensitive module — it contains the state machine that drives durable execution. Changes here require careful review.
- **`sdk-api`** and **`sdk-api-kotlin`** are the public API surfaces. Changes here affect end users directly — maintain backward compatibility.
- **`sdk-core` shading**: Dependencies are shaded via the Shadow plugin, so shaded classes won't appear at their original package paths in the final jar.
- Proto files in `sdk-core/src/main/service-protocol/` define the Restate wire protocol — these come from an external spec and should not be modified without coordinating with the protocol definition.

## Things to Avoid

- Do NOT edit files under `build/generated/` — they are produced by annotation processors and KSP.
- Do NOT manually edit formatting — run `./gradlew spotlessApply` instead.
- Do NOT modify proto files without understanding the upstream protocol spec.