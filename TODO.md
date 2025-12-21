# TODO - Code Compliance Tasks

**Last Updated:** 2025-12-21
**Source:** Code compliance review against BEST_PRACTICES.md and ARCHITECTURE.md

This file tracks code compliance violations and technical debt. Keep this updated as tasks are completed.

---

## Critical Priority (Must Fix)

### Missing Unit Tests

Per BEST_PRACTICES.md:159 - "Every feature must have both unit tests and integration tests"

- [x] **Create `OpenApiHandlerTest.kt`** ✅ COMPLETED
  - Location: `src/test/kotlin/com/example/service/unit/handlers/OpenApiHandlerTest.kt`
  - Tests implemented:
    - Should return 200 status code ✅
    - Should return application/json content type ✅
    - Should return valid OpenAPI JSON ✅
    - Should contain health endpoint in paths ✅
    - Should contain components section with schemas ✅
  - Coverage: 5/5 tests passing

- [x] **Create `SwaggerUiHandlerTest.kt`** ✅ COMPLETED
  - Location: `src/test/kotlin/com/example/service/unit/handlers/SwaggerUiHandlerTest.kt`
  - Tests implemented:
    - Should return 200 status code ✅
    - Should return text/html content type ✅
    - Should return valid HTML ✅
    - Should contain Swagger UI title ✅
    - Should reference Swagger UI CSS ✅
    - Should reference Swagger UI JavaScript ✅
    - Should configure OpenAPI spec URL ✅
    - Should contain swagger-ui div element ✅
  - Coverage: 8/8 tests passing

- [x] **Create `MainVerticleTest.kt`** ✅ COMPLETED
  - Location: `src/test/kotlin/com/example/service/unit/MainVerticleTest.kt`
  - Tests implemented:
    - Should deploy verticle successfully ✅
    - Should start HTTP server on configured port ✅
    - Should start server on specified port ✅
    - Should register health check endpoint ✅
    - Should register openapi json endpoint ✅
    - Should register swagger endpoint ✅
  - Coverage: 6/6 tests passing

- [x] **Create `OpenApiGeneratorTest.kt`** ✅ COMPLETED
  - Location: `src/test/kotlin/com/example/service/unit/openapi/OpenApiGeneratorTest.kt`
  - Tests implemented:
    - Should generate valid JSON from OpenAPI spec ✅
    - Should enable pretty printing in generated JSON ✅
    - Should handle minimal OpenAPI spec ✅
    - Should serialize paths correctly ✅
    - Should serialize components schemas correctly ✅
    - Should serialize schema references correctly ✅
    - Should handle empty paths ✅
  - Coverage: 7/7 tests passing

- [ ] **Create `OpenTelemetryConfigTest.kt`** (optional - may be challenging)
  - Location: `src/test/kotlin/com/example/service/unit/config/OpenTelemetryConfigTest.kt`
  - Tests needed:
    - Should initialize with correct service name
    - Should configure OTLP endpoint
    - (Note: May require refactoring for testability)

### Missing Integration Tests

- [x] **Create `OpenApiIntegrationTest.kt`** ✅ COMPLETED
  - Location: `src/test/kotlin/com/example/service/integration/api/OpenApiIntegrationTest.kt`
  - Tests implemented:
    - GET /openapi.json should return 200 with valid JSON ✅
    - OpenAPI spec should be valid OpenAPI 3.0 specification ✅
    - OpenAPI spec should contain health endpoint documentation ✅
    - OpenAPI spec should contain components with HealthStatus schema ✅
    - OpenAPI spec should use schema references not inline definitions ✅
    - GET /swagger should return 200 with HTML page ✅
    - Swagger UI page should contain Swagger UI elements ✅
    - Swagger UI should be configured to load openapi json ✅
  - Coverage: 8/8 tests passing

### Undocumented API Endpoints

Per BEST_PRACTICES.md:762 - "Document all endpoints in ApiSpecification.kt"

- [x] **Document `/openapi.json` endpoint in ApiSpecification.kt** ✅ COMPLETED
  - File: `src/main/kotlin/com/example/service/openapi/ApiSpecification.kt`
  - Implemented with:
    - operationId: "getOpenApiSpec" ✅
    - summary: "Get OpenAPI specification" ✅
    - description ✅
    - tag: "Documentation" ✅
    - 200 response documentation ✅

- [x] **Document `/swagger` endpoint in ApiSpecification.kt** ✅ COMPLETED
  - File: `src/main/kotlin/com/example/service/openapi/ApiSpecification.kt`
  - Implemented with:
    - operationId: "getSwaggerUI" ✅
    - summary: "Swagger UI interface" ✅
    - description ✅
    - tag: "Documentation" ✅
    - 200 response documentation ✅

---

## High Priority

### Missing KDoc Comments

Per BEST_PRACTICES.md:70 - "Use KDoc for public APIs"

- [ ] **Add KDoc to `HealthHandler` class**
  - File: `src/main/kotlin/com/example/service/handlers/HealthHandler.kt:6`
  - Example:
    ```kotlin
    /**
     * Handler for health check endpoint
     * Returns the current health status of the service
     */
    class HealthHandler {
    ```

- [ ] **Add KDoc to `OpenTelemetryConfig` object**
  - File: `src/main/kotlin/com/example/service/config/OpenTelemetryConfig.kt:15`
  - Example:
    ```kotlin
    /**
     * Configuration for OpenTelemetry distributed tracing
     * Initializes the OTel SDK with OTLP exporter for Jaeger
     */
    object OpenTelemetryConfig {
    ```

- [ ] **Add KDoc to `MainVerticle` class**
  - File: `src/main/kotlin/com/example/service/MainVerticle.kt:15`
  - Example:
    ```kotlin
    /**
     * Main application verticle
     * Sets up HTTP server with health check and API documentation endpoints
     */
    class MainVerticle : AbstractVerticle() {
    ```

- [ ] **Add KDoc to `main()` function**
  - File: `src/main/kotlin/com/example/service/Main.kt:11`
  - Example:
    ```kotlin
    /**
     * Application entry point
     * Initializes OpenTelemetry and deploys the main verticle
     */
    fun main() {
    ```

- [ ] **Add KDoc to public DSL functions**
  - File: `src/main/kotlin/com/example/service/openapi/dsl/OpenApiDsl.kt`
  - Functions needing KDoc:
    - `info()`, `path()`, `get()`, `post()`, `put()`, `delete()`, `patch()`
    - `response()`, `requestBody()`, `tag()`
    - `jsonContent()`
  - Example:
    ```kotlin
    /**
     * Configure API info section (title, version, description)
     */
    fun info(block: InfoBuilder.() -> Unit) {
    ```

---

## Notes

### Fully Compliant Areas ✅

The following areas are in excellent compliance and should be maintained:

- ✅ **No wildcard imports** - STRICT rule fully enforced (0 violations)
- ✅ **Indentation** - All files use 4-space indentation
- ✅ **Line length** - No lines exceed 120 characters
- ✅ **Naming conventions** - All classes, functions, files follow standards
- ✅ **Error handling** - Proper logging with context, no silent failures
- ✅ **Null safety** - No use of `!!` operator
- ✅ **Test quality** - Existing tests follow AAA pattern, use backticks, AssertJ
- ✅ **Code organization** - Proper directory structure

### Test Coverage Status

**Current Coverage:**
- ✅ HealthHandler - Has unit + integration tests
- ✅ ApiSpecification - Has unit tests
- ✅ OpenApiHandler - Has unit tests (5 tests)
- ✅ SwaggerUiHandler - Has unit tests (8 tests)
- ✅ MainVerticle - Has unit tests (6 tests)
- ✅ OpenApiGenerator - Has unit tests (7 tests)
- ✅ OpenAPI/Swagger endpoints - Has integration tests (8 tests)
- ⚠️ OpenTelemetryConfig - No tests (optional, may require refactoring)

**Target:** 80% minimum, 90%+ for business logic (per BEST_PRACTICES.md)

### Completion Tracking

**Critical Priority:** 8/9 complete (88.9%)
**High Priority:** 0/5 complete (0%)
**Overall:** 8/14 complete (57.1%)

---

## Instructions for Maintaining This File

1. **Check off tasks** by replacing `- [ ]` with `- [x]` when complete
2. **Update completion tracking** percentages when tasks are done
3. **Update "Last Updated" date** when making changes
4. **Remove completed tasks** once they've been verified (or move to a "Completed" section)
5. **Add new tasks** as they're discovered, maintaining priority order
6. **Reference this file** in code reviews and planning sessions

---

**Remember:** All new features must include both unit and integration tests before being considered complete.
