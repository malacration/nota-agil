# Repository Guidelines

## Project Structure & Module Organization
- Kotlin code sits in `src/main/kotlin`; group packages by feature under `br.andrew`.
- WSDL files live in `src/main/resources/wsdl`; regenerate the Axis stubs in `src/generated/java` and never hand-edit them.
- Config in `src/main/resources/application.properties`; profile overrides like `application-invent.properties`; tests mirror packages in `src/test/kotlin`.

## Build, Test & Development Commands
- `./gradlew import-ws` regenerates Axis client code after changing any WSDL.
- `./gradlew build` compiles Kotlin, regenerates WSDL stubs, runs tests, and assembles the artifact.
- `./gradlew test` executes the JUnit 5 suite.
- `./gradlew bootRun` starts the Spring Boot app with the active profile from `SPRING_PROFILES_ACTIVE`.
- `docker-compose up mongo` starts a local MongoDB with the default credentials.

## Coding Style & Naming Conventions
- Follow Kotlin official style: 4-space indentation, trailing commas on multiline literals, and compact expression bodies when they aid clarity.
- Name types with `PascalCase`, functions and beans with `camelCase`, and constants with `UPPER_SNAKE_CASE`; generated code resolves to `br.andrew.wsdl.<wsdlName>`.
- Prefer constructor injection with Spring stereotypes; keep validation helpers beside the REST endpoints that use them.

## Testing Guidelines
- Tests rely on `spring-boot-starter-test` and `kotlin-test-junit5`; default to JUnit 5 annotations.
- Name files `<Subject>Test.kt` and individual tests with backtick-quoted phrases describing behavior.
- Use `@DataMongoTest` for slices and `@SpringBootTest` for full-stack coverage.
- Ensure each REST path has at least one happy-path and one validation test.

## Commit & Pull Request Guidelines
- Commits are short, action-oriented statements in Portuguese (e.g., `Ajusta validacao de token`); keep them under 60 characters and skip trailing punctuation.
- Rebase before opening a PR, include context, reproduction notes, and link relevant Jira or GitHub issues.
- Highlight schema or WSDL changes explicitly, including regenerated outputs and impacted services.
- Attach screenshots or console snippets for user-facing changes, and list new environment variables or Mongo indexes.

## Comunicação & Idioma
- Utilize português-BR em commits, descrições de PR e respostas a revisões.
- Registre decisões técnicas e notas de deploy em português para manter o histórico alinhado com a equipe.

## Configuration & Security Tips
- Never commit real credentials; rely on environment overrides or a local `.env` sourced by Gradle.
- Reset MongoDB users after local testing—the compose defaults are public and must not leak to shared environments.
- When adding profiles, duplicate only the necessary overrides and document the target environment at the top of the file.
