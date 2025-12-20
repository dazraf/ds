# Best Practices

**Last Updated:** 2025-12-20

This document outlines coding standards, conventions, and best practices for this project. All contributors should follow these guidelines to maintain code quality and consistency.

## Table of Contents

- [General Principles](#general-principles)
- [Code Style](#code-style)
- [Naming Conventions](#naming-conventions)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Security](#security)
- [Performance](#performance)
- [Git Workflow](#git-workflow)
- [Documentation](#documentation)

## General Principles

### Keep It Simple
- Avoid over-engineering
- Write code that solves the current problem
- Don't add features or abstractions until they're needed
- Prefer straightforward solutions over clever ones

### Code Quality
- Write self-documenting code with clear variable and function names
- Keep functions small and focused on a single responsibility
- Aim for high cohesion and loose coupling
- Follow DRY (Don't Repeat Yourself) but avoid premature abstraction

### Consistency
- Follow existing patterns in the codebase
- When in doubt, match the style of surrounding code
- Use linters and formatters to enforce consistency

## Code Style

### Formatting
- **Formatter**: Kotlin official style guide with ktlint
- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters maximum
- **Imports**: No wildcard imports (except for testing DSLs)

### File Organization
- One class per file (unless tightly coupled sealed classes/data classes)
- File name should match the main class name
- Group related functionality together
- Keep files under 300-400 lines when possible
- Organize imports:
  1. Standard library (`kotlin.*`, `java.*`)
  2. Third-party libraries
  3. Project imports
- Remove unused imports

### Comments
- Write comments that explain "why", not "what"
- Avoid obvious comments
- Update comments when code changes
- Use KDoc for public APIs (similar to JavaDoc)
- Use TODO comments with tickets: `// TODO(TICKET-123): Description`

### Kotlin-Specific Conventions
- Prefer `val` over `var` for immutability
- Use data classes for DTOs and value objects
- Leverage Kotlin's null safety - avoid `!!` operator
- Use named arguments for functions with multiple parameters
- Prefer expression body functions when appropriate: `fun add(a: Int, b: Int) = a + b`
- Use `when` expressions over multiple `if-else` chains
- Leverage extension functions to enhance readability

## Naming Conventions

### Variables & Properties
- Use descriptive names: `userEmail` not `ue`
- Boolean variables should read as questions: `isActive`, `hasPermission`, `canEdit`
- Avoid negatives: `isEnabled` not `isNotDisabled`
- Use camelCase for variables and properties: `firstName`, `totalAmount`
- Constants: UPPER_SNAKE_CASE for compile-time constants
  - `const val MAX_RETRIES = 3`
  - `const val API_BASE_URL = "https://api.example.com"`

### Classes & Interfaces
- Use PascalCase: `UserService`, `OrderRepository`, `HttpHandler`
- Interfaces should be descriptive nouns: `UserRepository`, `MessageHandler`
- Don't prefix interfaces with `I` (not IUserRepository)

### Functions/Methods
- Use camelCase with verb prefixes: `getUser()`, `createOrder()`, `validateInput()`
- Be consistent with verb choices:
  - `get`/`fetch`: retrieve data
  - `create`/`build`: instantiate new objects
  - `update`/`modify`: modify existing data
  - `delete`/`remove`: remove data
  - `validate`/`check`: check data validity
  - `handle`: event/error handling
  - `process`: data processing
- Suspend functions (coroutines) don't need special naming - same conventions apply

### Files & Directories
- Kotlin files use PascalCase matching the main class: `UserService.kt`
- Package names use lowercase: `com.example.service`
- Test files mirror source with `Test` suffix: `UserServiceTest.kt`
- Directory structure follows package structure

## Error Handling

### Principles
- Fail fast and fail loudly
- Always handle errors, never silently swallow them
- Provide meaningful error messages
- Log errors with appropriate context

### Implementation
```kotlin
// Good - Kotlin exception handling
try {
    riskyOperation()
} catch (e: Exception) {
    logger.error("Failed to process user data: userId=$userId", e)
    throw ServiceException("Unable to process user data", e)
}

// Good - Using Result type for operations that may fail
fun processData(): Result<Data> = runCatching {
    riskyOperation()
}.onFailure { e ->
    logger.error("Failed to process data", e)
}

// Bad - Silent failure
try {
    riskyOperation()
} catch (e: Exception) {
    // Silent failure - never do this!
}
```

### Error Types
- Use custom exception classes for different error categories
- Extend appropriate base exception types
- Include relevant context in exception messages
- Don't expose internal errors to clients
- Consider using sealed classes for domain-specific error types
- Leverage Kotlin's `Result<T>` type for operations that may fail

## Testing

**Philosophy**: Comprehensive testing is non-negotiable. Every feature must have both unit tests and integration tests. Tests are not optional—they are part of the definition of "done."

### Test Framework & Tools
- **Framework**: JUnit 5 (Jupiter)
- **Assertions**: AssertJ for fluent, readable assertions
- **Mocking**: MockK (Kotlin-specific mocking library)
- **Integration Testing**: Testcontainers (for PostgreSQL and other dependencies)
- **Vert.x Testing**: VertxExtension for async test support

### Test Organization

```
/src/test/kotlin/[package]
  /unit              # Unit tests (fast, isolated, no external dependencies)
    /handlers
    /services
    /repositories
    /models
  /integration       # Integration tests (slower, real dependencies)
    /api             # End-to-end API tests
    /database        # Database integration tests
    /verticles       # Verticle deployment and integration tests
  /fixtures          # Test data builders and fixtures
  /testcontainers    # Testcontainer configuration
```

### Test Coverage Requirements

- **Overall Coverage**: Minimum 80%, target 90%+
- **Unit Test Coverage**: 90%+ for business logic (services, handlers, utilities)
- **Integration Test Coverage**: All critical paths and external integrations
- **All new features**: Must include both unit and integration tests
- **Bug fixes**: Must include regression tests (unit or integration as appropriate)

### Unit Tests

**Purpose**: Test individual components in isolation, fast execution, no external dependencies.

**Characteristics**:
- No database connections
- No network calls
- No file I/O
- Use mocks for dependencies
- Execute in milliseconds
- Can run in parallel

**Naming Convention**: `[ClassName]Test.kt`

**Example - Service Unit Test**:
```kotlin
@ExtendWith(MockKExtension::class)
class UserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userService = UserService(userRepository)
    }

    @Test
    fun `should return user when found by id`() {
        // Arrange
        val userId = "123"
        val expectedUser = User(id = userId, email = "test@example.com")
        every { userRepository.findById(userId) } returns expectedUser

        // Act
        val result = userService.getUser(userId)

        // Assert
        assertThat(result)
            .isNotNull
            .extracting("id", "email")
            .containsExactly(userId, "test@example.com")
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    fun `should throw exception when user not found`() {
        // Arrange
        val userId = "nonexistent"
        every { userRepository.findById(userId) } returns null

        // Act & Assert
        assertThatThrownBy { userService.getUser(userId) }
            .isInstanceOf(UserNotFoundException::class.java)
            .hasMessage("User not found: $userId")
    }
}
```

**Example - Handler Unit Test**:
```kotlin
class UserHandlerTest {

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var routingContext: RoutingContext

    @MockK
    private lateinit var response: HttpServerResponse

    private lateinit var userHandler: UserHandler

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        userHandler = UserHandler(userService)
        every { routingContext.response() } returns response
        every { response.putHeader(any(), any()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    @Test
    fun `should return user JSON when user exists`() {
        // Arrange
        val userId = "123"
        val user = User(id = userId, email = "test@example.com")
        every { routingContext.pathParam("id") } returns userId
        every { userService.getUser(userId) } returns user

        // Act
        userHandler.getUser(routingContext)

        // Assert
        verify { response.setStatusCode(200) }
        verify { response.putHeader("Content-Type", "application/json") }
        verify { response.end(match { it.contains("test@example.com") }) }
    }
}
```

### Integration Tests

**Purpose**: Test components working together with real dependencies (database, message queues, etc.).

**Characteristics**:
- Use real database (via Testcontainers)
- Test actual HTTP endpoints
- Test Vert.x verticle deployment
- Slower execution (seconds)
- May need to run sequentially

**Naming Convention**: `[ClassName]IntegrationTest.kt`

**Test Annotations**:
```kotlin
@ExtendWith(VertxExtension::class)  // For async Vert.x testing
@Testcontainers                      // For Testcontainers support
```

**Example - Database Integration Test**:
```kotlin
@ExtendWith(VertxExtension::class)
@Testcontainers
class UserRepositoryIntegrationTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private lateinit var pgPool: PgPool
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        val connectOptions = PgConnectOptions()
            .setPort(postgresContainer.firstMappedPort)
            .setHost(postgresContainer.host)
            .setDatabase(postgresContainer.databaseName)
            .setUser(postgresContainer.username)
            .setPassword(postgresContainer.password)

        val poolOptions = PoolOptions().setMaxSize(5)
        pgPool = PgPool.pool(vertx, connectOptions, poolOptions)
        userRepository = UserRepository(pgPool)

        // Run migrations or setup schema
        setupSchema(testContext)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        pgPool.close()
            .onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `should save and retrieve user from database`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val user = User(id = "123", email = "test@example.com", name = "Test User")

        userRepository.save(user)
            .compose { userRepository.findById("123") }
            .onComplete(testContext.succeeding { retrievedUser ->
                testContext.verify {
                    assertThat(retrievedUser)
                        .isNotNull
                        .extracting("id", "email", "name")
                        .containsExactly("123", "test@example.com", "Test User")
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should return empty when user does not exist`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        userRepository.findById("nonexistent")
            .onComplete(testContext.succeeding { result ->
                testContext.verify {
                    assertThat(result).isNull()
                    testContext.completeNow()
                }
            })
    }
}
```

**Example - API Integration Test**:
```kotlin
@ExtendWith(VertxExtension::class)
@Testcontainers
class UserApiIntegrationTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15-alpine")

        private const val TEST_PORT = 8888
    }

    private lateinit var webClient: WebClient

    @BeforeEach
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        // Configure and deploy the main verticle
        val config = JsonObject()
            .put("http.port", TEST_PORT)
            .put("db.host", postgresContainer.host)
            .put("db.port", postgresContainer.firstMappedPort)
            .put("db.database", postgresContainer.databaseName)

        vertx.deployVerticle(MainVerticle(), DeploymentOptions().setConfig(config))
            .onComplete(testContext.succeedingThenComplete())

        webClient = WebClient.create(vertx)
    }

    @Test
    fun `GET user should return 200 with user JSON`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        // Arrange - create user in database first
        val userId = "test-user-123"
        createTestUser(userId, testContext)

        // Act
        webClient.get(TEST_PORT, "localhost", "/api/users/$userId")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    // Assert
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type"))
                        .contains("application/json")

                    val body = response.bodyAsJsonObject()
                    assertThat(body.getString("id")).isEqualTo(userId)
                    assertThat(body.getString("email")).isNotEmpty()

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `GET user should return 404 when user not found`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        webClient.get(TEST_PORT, "localhost", "/api/users/nonexistent")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(404)
                    testContext.completeNow()
                }
            })
    }
}
```

### Testing Async/Reactive Code (Vert.x)

**Use VertxExtension**:
```kotlin
@ExtendWith(VertxExtension::class)
class AsyncOperationTest {

    @Test
    fun `should handle async operation`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val checkpoint = testContext.checkpoint()

        asyncOperation()
            .onComplete(testContext.succeeding { result ->
                testContext.verify {
                    assertThat(result).isNotNull()
                    checkpoint.flag()
                }
            })
    }

    @Test
    fun `should handle multiple async operations`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val checkpoint = testContext.checkpoint(2)  // Expect 2 completions

        asyncOperation1()
            .onComplete(testContext.succeeding {
                checkpoint.flag()
            })

        asyncOperation2()
            .onComplete(testContext.succeeding {
                checkpoint.flag()
            })
    }
}
```

### AssertJ Assertions

```kotlin
// Use AssertJ's fluent API
assertThat(user.email)
    .isNotNull()
    .isEqualTo("user@example.com")

// Collections
assertThat(users)
    .hasSize(3)
    .extracting("email")
    .contains("user1@example.com", "user2@example.com")

// Exceptions
assertThatThrownBy { service.processInvalidData() }
    .isInstanceOf(ValidationException::class.java)
    .hasMessageContaining("Invalid input")

// Optional/nullable values
assertThat(optionalUser)
    .isPresent
    .get()
    .extracting("email")
    .isEqualTo("test@example.com")
```

### Test Structure & Best Practices

- **Follow AAA pattern**: Arrange, Act, Assert
- **One logical assertion per test** (AssertJ chains are fine)
- **Use descriptive test names** with backticks:
  ```kotlin
  @Test
  fun `should return 404 when user not found`() { ... }
  ```
- **Use test fixtures** for common test data
- **Clean up resources** in `@AfterEach` (close connections, delete test data)
- **Avoid test interdependencies** - each test should be runnable in isolation
- **Use `@Nested` classes** to group related tests

### What to Test

**Unit Tests**:
- Business logic in services
- Data validation and transformation
- Error handling and exception scenarios
- Edge cases and boundary conditions
- Utility functions

**Integration Tests**:
- Database queries and transactions
- HTTP API endpoints (full request/response cycle)
- Verticle deployment and configuration
- Message queue interactions
- External service integrations
- Authentication and authorization flows

### What Not to Test

- Third-party library internals
- Trivial getters/setters (data classes)
- Framework code (Vert.x internals)
- Auto-generated code
- Configuration file parsing (unless custom logic)

### Mocking Guidelines

- **Unit tests**: Mock all external dependencies (repositories, HTTP clients, etc.)
- **Integration tests**: Use real dependencies (database via Testcontainers)
- **Use MockK** for Kotlin-friendly mocking with nice DSL
- **Don't over-mock**: If mocking becomes complex, consider an integration test instead
- **Verify interactions** when behavior matters, not just return values

### Test Data Management

```kotlin
// Use object mothers or builders for test data
object UserFixtures {
    fun defaultUser(
        id: String = UUID.randomUUID().toString(),
        email: String = "test@example.com",
        name: String = "Test User"
    ) = User(id = id, email = email, name = name)

    fun adminUser() = defaultUser(
        email = "admin@example.com",
        name = "Admin User"
    )
}

// Usage
val user = UserFixtures.defaultUser(email = "custom@example.com")
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run only unit tests
./gradlew test --tests '*Test'

# Run only integration tests
./gradlew test --tests '*IntegrationTest'

# Run with coverage
./gradlew test jacocoTestReport

# Run specific test class
./gradlew test --tests 'UserServiceTest'

# Run tests in continuous mode
./gradlew test --continuous
```

### Code Coverage

- Use JaCoCo for code coverage reporting
- Enforce minimum coverage thresholds in build
- Review coverage reports in CI/CD pipeline
- Focus on meaningful coverage, not just percentages

## Security

### Input Validation
- Validate all user input
- Sanitize data before storage or output
- Use parameterized queries to prevent SQL injection
- Validate on both client and server

### Authentication & Authorization
- Never trust client-side authentication
- Use established libraries/frameworks
- Implement principle of least privilege
- Expire sessions appropriately

### Secrets Management
- Never commit secrets to version control
- Use environment variables or secret management services
- Rotate credentials regularly

### Common Vulnerabilities
Prevent OWASP Top 10:
- SQL Injection
- XSS (Cross-Site Scripting)
- CSRF (Cross-Site Request Forgery)
- Insecure direct object references
- Security misconfiguration

## Performance

### Database
- Use indexes appropriately
- Avoid N+1 queries
- Use connection pooling
- Monitor slow queries

### Caching
- Cache expensive operations
- Set appropriate TTLs
- Implement cache invalidation strategy

### API Design
- Implement pagination for large result sets
- Use appropriate HTTP methods and status codes
- Support compression (gzip)
- Rate limit endpoints

### Code Optimization
- Profile before optimizing
- Optimize bottlenecks, not everything
- Don't sacrifice readability for marginal gains

## Git Workflow

### Branching Strategy
[e.g., Git Flow, GitHub Flow, trunk-based development]

### Commit Messages
- Use clear, descriptive commit messages
- Format: `type(scope): description`
- Types:
  - `feat`: new feature
  - `fix`: bug fix
  - `docs`: documentation changes
  - `style`: code formatting (no logic change)
  - `refactor`: code restructuring (no behavior change)
  - `test`: adding or updating tests
  - `chore`: build, dependencies, tooling
  - `perf`: performance improvements
- Examples:
  - `feat(auth): add OAuth2 login support`
  - `fix(database): resolve connection pool exhaustion`
  - `refactor(handlers): extract validation logic to separate class`
  - `chore(deps): update Vert.x to 4.5.0`

### Pull Requests
- Keep PRs focused and reasonably sized
- Write clear PR descriptions
- Link related issues
- Request reviews from appropriate team members
- Address review comments promptly

### Code Review Guidelines
- Review for correctness, readability, and maintainability
- Check for security vulnerabilities
- Ensure tests are included and passing
- Verify documentation is updated
- Be constructive and respectful

## Documentation

### Code Documentation
- Document public APIs
- Include usage examples for complex functions
- Keep documentation up to date with code changes

### README Files
- Each service should have its own README
- Include setup instructions
- Document environment variables
- Provide usage examples

### Architecture Docs
- Update [ARCHITECTURE.md](./ARCHITECTURE.md) when making architectural changes
- Document design decisions and trade-offs
- Keep diagrams current
- **Use Mermaid** for all diagrams (architecture, sequence, flow charts, etc.)
  - Renders in GitHub and most markdown viewers
  - Version-controllable as text
  - Easy to update and maintain

### API Documentation
- [Specify format: OpenAPI/Swagger, etc.]
- Document all endpoints
- Include request/response examples
- Document error responses

---

**Note**: These practices should evolve with the team and project. Suggest updates when you identify improvements.
