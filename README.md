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

### Start the Complete Local Stack

```bash
# 1. Start infrastructure (PostgreSQL, OTel Collector, Jaeger)
docker-compose up -d

# 2. Run the application
./gradlew run
```

### Verify the Stack

```bash
# Test the health endpoint
curl http://localhost:8080/api/health

# Expected response: {"status":"OK"}
```

## Service URLs

Once the stack is running, access the following services:

### Application Endpoints

| Service          | URL                                | Description                      |
|------------------|------------------------------------|----------------------------------|
| **API Base**     | http://localhost:8080              | Main application server          |
| **Health Check** | http://localhost:8080/api/health   | Service health status            |
| **Swagger UI**   | http://localhost:8080/swagger      | Interactive API documentation    |
| **OpenAPI Spec** | http://localhost:8080/openapi.json | OpenAPI 3.0 specification (JSON) |

### Infrastructure Services

| Service            | URL                    | Description                                                          |
|--------------------|------------------------|----------------------------------------------------------------------|
| **PostgreSQL**     | `localhost:5432`       | Database (user: `postgres`, password: `postgres`, database: `appdb`) |
| **Jaeger UI**      | http://localhost:16686 | Distributed tracing visualization                                    |
| **OTel Collector** | `localhost:4317`       | OpenTelemetry OTLP gRPC endpoint                                     |

### Development Tools

| Tool                       | Location                                    | Description                                                            |
|----------------------------|---------------------------------------------|------------------------------------------------------------------------|
| **JaCoCo Coverage Report** | `build/reports/jacoco/test/html/index.html` | Test coverage report (after running `./gradlew test jacocoTestReport`) |
| **Test Reports**           | `build/reports/tests/test/index.html`       | Test execution report (after running `./gradlew test`)                 |

### Stopping the Stack

```bash
# Stop the application (Ctrl+C)

# Stop infrastructure services
docker-compose down

# Stop and remove all data (including database)
docker-compose down -v
```

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
├── build.gradle.kts                    # Gradle build configuration
├── docker-compose.yml                  # Local development stack
├── otel-collector-config.yaml          # OpenTelemetry configuration
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/service/
│   │   │       ├── Main.kt                        # Application entry point
│   │   │       ├── MainVerticle.kt                # Main application verticle
│   │   │       ├── config/
│   │   │       │   └── OpenTelemetryConfig.kt     # OTel setup
│   │   │       ├── handlers/
│   │   │       │   ├── HealthHandler.kt           # Health check handler
│   │   │       │   ├── OpenApiHandler.kt          # OpenAPI spec endpoint
│   │   │       │   └── SwaggerUiHandler.kt        # Swagger UI endpoint
│   │   │       ├── models/
│   │   │       │   └── HealthStatus.kt            # Data models
│   │   │       └── openapi/
│   │   │           ├── ApiSpecification.kt        # OpenAPI spec definition
│   │   │           ├── OpenApiGenerator.kt        # JSON generator
│   │   │           └── dsl/
│   │   │               └── OpenApiDsl.kt          # Kotlin DSL for OpenAPI
│   │   └── resources/
│   │       ├── logback.xml                        # Logging configuration
│   │       └── application.conf                   # Application configuration
│   └── test/
│       └── kotlin/
│           └── com/example/service/
│               ├── unit/
│               │   ├── handlers/                  # Handler unit tests
│               │   └── openapi/                   # OpenAPI DSL tests
│               └── integration/
│                   └── api/                       # API integration tests
└── docs/
    ├── ARCHITECTURE.md                # Architecture documentation
    └── BEST_PRACTICES.md              # Coding standards
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
