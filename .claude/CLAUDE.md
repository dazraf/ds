# DS - Kotlin Vert.x Service Project

This project uses a reactive microservice architecture built with Kotlin, Eclipse Vert.x, and PostgreSQL.

## Critical Documentation

Before starting any work, familiarize yourself with:

### Project Overview & Setup
@README.md

### System Architecture & Design Patterns
@docs/ARCHITECTURE.md

### Development Standards & Best Practices
@docs/BEST_PRACTICES.md

### Outstanding Tasks & Technical Debt
@TODO.md

**IMPORTANT:** Always check and update TODO.md when:
- Completing any task listed in it
- Discovering new compliance violations
- Adding new features (ensure tests and docs are added to TODO if not complete)
- During code reviews

## Quick Reference

### Start the Complete Stack
```bash
docker-compose up -d    # Start PostgreSQL, OTel Collector, Jaeger
./gradlew run           # Run the application
```

### Service URLs
- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger
- **OpenAPI Spec**: http://localhost:8080/openapi.json
- **Jaeger UI**: http://localhost:16686
- **PostgreSQL**: localhost:5432 (user: postgres, password: postgres, db: appdb)

### Development Commands
```bash
./gradlew build                  # Build the project
./gradlew test                   # Run all tests
./gradlew test jacocoTestReport  # Generate coverage report
```

### Testing Standards
- **ALL features must have both unit and integration tests**
- **Minimum coverage**: 80% overall, 90%+ for business logic
- **Unit tests**: Fast, isolated, mocked dependencies
- **Integration tests**: Real dependencies via Testcontainers

### Code Style
- **No wildcard imports** (strict enforcement)
- 4-space indentation
- 120 character line limit
- Kotlin official style guide
- Do not leave unused imports or variables

### API Documentation
- All API endpoints defined in `src/main/kotlin/com/example/service/openapi/ApiSpecification.kt`
- Uses Kotlin DSL with Swagger Core for type-safe OpenAPI generation
- Schemas use `$ref` references to components, not inline definitions

### Git Workflow
- Commit message format: `type(scope): description`
- Types: feat, fix, docs, test, refactor, chore
- Include co-authorship footer for Claude-generated code

## Technology Stack
- **Language**: Kotlin + JVM 17
- **Framework**: Eclipse Vert.x (reactive)
- **Database**: PostgreSQL
- **Tracing**: OpenTelemetry + Jaeger
- **Testing**: JUnit 5, AssertJ, MockK, Testcontainers
- **API Docs**: OpenAPI 3.0 via Kotlin DSL + Swagger Core
