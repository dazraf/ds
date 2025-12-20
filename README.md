# DS - Kotlin Vert.x Service

A reactive microservice built with Kotlin, Eclipse Vert.x, and PostgreSQL, featuring OpenTelemetry tracing and comprehensive testing.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details on:
- Technology stack (Kotlin, Vert.x, PostgreSQL, OpenTelemetry)
- Service architecture and patterns
- Data flow diagrams
- Infrastructure setup

## Best Practices

See [docs/BEST_PRACTICES.md](docs/BEST_PRACTICES.md) for:
- Kotlin code style and conventions
- Comprehensive testing guidelines (unit + integration)
- Security best practices
- Git workflow and commit standards
- API documentation requirements

## Prerequisites

- JDK 17 or higher
- Docker and Docker Compose (for local development)

## Quick Start

### 1. Start Infrastructure

Start PostgreSQL, OpenTelemetry Collector, and Jaeger:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- OTel Collector on port 4317 (OTLP gRPC)
- Jaeger UI on port 16686 (http://localhost:16686)

### 2. Run the Application

```bash
./gradlew run
```

The service will start on port 8080.

### 3. Test the Health Endpoint

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "OK"
}
```

## API Documentation

Once the service is running, access:
- **Swagger UI**: http://localhost:8080/swagger (coming soon)
- **OpenAPI Spec**: http://localhost:8080/openapi.json (coming soon)

## Development

### Run Tests

```bash
# Run all tests
./gradlew test

# Run only unit tests
./gradlew test --tests '*Test'

# Run only integration tests
./gradlew test --tests '*IntegrationTest'

# Run with coverage report
./gradlew test jacocoTestReport
```

### Build

```bash
./gradlew build
```

### Code Style

This project follows strict Kotlin coding standards:
- No wildcard imports
- 4-space indentation
- 120 character line limit
- See [BEST_PRACTICES.md](docs/BEST_PRACTICES.md) for details

## Project Structure

```
.
├── build.gradle.kts              # Gradle build configuration
├── docker-compose.yml             # Local development stack
├── otel-collector-config.yaml    # OpenTelemetry configuration
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/service/
│   │   │       ├── MainVerticle.kt       # Main application verticle
│   │   │       └── handlers/
│   │   │           └── HealthHandler.kt  # Health check handler
│   │   └── resources/
│   │       ├── logback.xml               # Logging configuration
│   │       └── application.conf          # Application configuration
│   └── test/
│       └── kotlin/
│           └── com/example/service/
│               ├── unit/                  # Unit tests
│               └── integration/           # Integration tests
└── docs/
    ├── ARCHITECTURE.md            # Architecture documentation
    └── BEST_PRACTICES.md          # Coding standards
```

## Observability

### Viewing Traces

1. Access Jaeger UI: http://localhost:16686
2. Select service "ds-service"
3. Click "Find Traces"

### Logs

Logs are written to:
- Console (stdout)
- `logs/application.log` (with rotation)

## Testing Philosophy

This project maintains comprehensive test coverage:
- **Unit tests**: Fast, isolated, mocked dependencies
- **Integration tests**: Real dependencies via Testcontainers
- **Target coverage**: 80% minimum, 90%+ for business logic

All features must include both unit and integration tests.

## License

[Add license information]
