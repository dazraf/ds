# TODO - Code Compliance Tasks

**Last Updated:** 2025-12-21
**Source:** Code compliance review against BEST_PRACTICES.md and ARCHITECTURE.md

This file tracks code compliance violations and technical debt. Keep this updated as tasks are completed.

---

## Critical Priority (Must Fix)

### Missing Unit Tests

Per BEST_PRACTICES.md:159 - "Every feature must have both unit tests and integration tests"

- [ ] **Create `OpenApiHandlerTest.kt`**
  - Location: `src/test/kotlin/com/example/service/unit/handlers/OpenApiHandlerTest.kt`
  - Tests needed:
    - Should return 200 status code
    - Should return application/json content type
    - Should return valid OpenAPI JSON
  - Target coverage: 90%+

- [ ] **Create `SwaggerUiHandlerTest.kt`**
  - Location: `src/test/kotlin/com/example/service/unit/handlers/SwaggerUiHandlerTest.kt`
  - Tests needed:
    - Should return 200 status code
    - Should return text/html content type
    - Should return valid HTML with Swagger UI references
  - Target coverage: 90%+

- [ ] **Create `MainVerticleTest.kt`**
  - Location: `src/test/kotlin/com/example/service/unit/MainVerticleTest.kt`
  - Tests needed:
    - Should create router with all routes
    - Should register health endpoint
    - Should register OpenAPI endpoints
    - Should read configuration correctly
  - Target coverage: 90%+

- [ ] **Create `OpenApiGeneratorTest.kt`**
  - Location: `src/test/kotlin/com/example/service/unit/openapi/OpenApiGeneratorTest.kt`
  - Tests needed:
    - Should generate valid JSON from OpenAPI spec
    - Should enable pretty printing
    - Should handle empty/minimal specs
  - Target coverage: 90%+

- [ ] **Create `OpenTelemetryConfigTest.kt`** (optional - may be challenging)
  - Location: `src/test/kotlin/com/example/service/unit/config/OpenTelemetryConfigTest.kt`
  - Tests needed:
    - Should initialize with correct service name
    - Should configure OTLP endpoint
    - (Note: May require refactoring for testability)

### Missing Integration Tests

- [ ] **Create `OpenApiIntegrationTest.kt`**
  - Location: `src/test/kotlin/com/example/service/integration/api/OpenApiIntegrationTest.kt`
  - Tests needed:
    - GET /openapi.json should return 200 with valid JSON
    - OpenAPI spec should contain all documented endpoints
    - Response should be valid OpenAPI 3.0 specification
    - GET /swagger should return 200 with HTML page
    - Swagger UI page should load without errors

### Undocumented API Endpoints

Per BEST_PRACTICES.md:762 - "Document all endpoints in ApiSpecification.kt"

- [ ] **Document `/openapi.json` endpoint in ApiSpecification.kt**
  - File: `src/main/kotlin/com/example/service/openapi/ApiSpecification.kt`
  - Currently implemented: MainVerticle.kt:49
  - Required fields:
    - operationId: "getOpenApiSpec"
    - summary: "Get OpenAPI specification"
    - description
    - tag: "Documentation"
    - 200 response documentation

- [ ] **Document `/swagger` endpoint in ApiSpecification.kt**
  - File: `src/main/kotlin/com/example/service/openapi/ApiSpecification.kt`
  - Currently implemented: MainVerticle.kt:52
  - Required fields:
    - operationId: "getSwaggerUI"
    - summary: "Swagger UI interface"
    - description
    - tag: "Documentation"
    - 200 response documentation

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
- ❌ 5 components with NO tests (see Critical Priority section)

**Target:** 80% minimum, 90%+ for business logic (per BEST_PRACTICES.md)

### Completion Tracking

**Critical Priority:** 0/9 complete (0%)
**High Priority:** 0/5 complete (0%)
**Overall:** 0/14 complete (0%)

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
