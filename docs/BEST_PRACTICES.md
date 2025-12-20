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

### Test Framework
- **Framework**: JUnit 5 (Jupiter)
- **Assertions**: AssertJ for fluent, readable assertions
- **Mocking**: MockK (Kotlin-specific mocking library)

### Test Coverage
- Aim for 80%+ code coverage
- All new features must include tests
- Bug fixes should include regression tests

### Test Structure
- Follow AAA pattern: Arrange, Act, Assert
- One logical assertion per test (AssertJ chains are fine)
- Use descriptive test names with backticks:
  ```kotlin
  @Test
  fun `should return 404 when user not found`() { ... }
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
```

### What to Test
- Happy paths
- Error cases and exception scenarios
- Edge cases and boundary conditions
- Integration between components
- Async/reactive flows (Vert.x specific)

### What Not to Test
- Third-party library internals
- Trivial getters/setters (data classes)
- Framework code
- Auto-generated code

### Mocking
- Mock external dependencies (databases, APIs, external services)
- Use MockK for Kotlin-friendly mocking
- Don't mock everything - test real integrations when valuable
- Keep mocks simple and maintainable
- Consider using test containers for database testing

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
