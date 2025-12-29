package com.example.service

import com.example.service.database.DatabaseConfig
import com.example.service.database.DatabaseManager
import com.example.service.database.LiquibaseRunner
import com.example.service.handlers.HealthHandler
import com.example.service.handlers.NamespaceHandler
import com.example.service.handlers.OpenApiHandler
import com.example.service.handlers.SwaggerUiHandler
import com.example.service.repositories.NamespaceRepository
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.tracing.TracingPolicy
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Main application verticle.
 *
 * Sets up the HTTP server with database infrastructure and API endpoints.
 * Initializes DatabaseManager, runs migrations, and configures routing
 * with OpenTelemetry tracing enabled.
 */
class MainVerticle : CoroutineVerticle() {

    private lateinit var databaseManager: DatabaseManager
    private lateinit var namespaceRepository: NamespaceRepository

    override suspend fun start() {
        val httpConfig = config.getJsonObject("http") ?: JsonObject()
        val port = httpConfig.getInteger("port", 8080)
        val host = httpConfig.getString("host", "0.0.0.0")

        // Initialize database infrastructure
        initializeDatabase()

        val router = createRouter()
        val serverOptions = HttpServerOptions()
            .setPort(port)
            .setHost(host)
            .setTracingPolicy(TracingPolicy.ALWAYS)

        logger.info {
            @Suppress("HttpUrlsUsage")
            "Starting server on http://$host:$port"
        }
        vertx.createHttpServer(serverOptions)
            .requestHandler(router)
            .listen()
            .coAwait()

        logger.info { "HTTP server started on $host:$port" }
    }

    private suspend fun initializeDatabase() {
        logger.info { "Initializing database infrastructure..." }

        // Load database configs
        val adminConfig = DatabaseConfig.fromJsonObject(config.getJsonObject("database").getJsonObject("admin"))
        val registryConfig = DatabaseConfig.fromJsonObject(config.getJsonObject("database").getJsonObject("registry"))

        // Initialize DatabaseManager
        databaseManager = DatabaseManager(adminConfig, registryConfig, vertx)
        logger.info { "DatabaseManager initialized" }

        // Run migrations on registry database
        val registryMigrationRunner = LiquibaseRunner.from(registryConfig)
        registryMigrationRunner.runMigrations("db/changelog/registry/db.changelog-master.yaml")
        logger.info { "Registry database migrations completed" }

        // Initialize repositories
        namespaceRepository = NamespaceRepository(
            registryPool = databaseManager.getRegistryConnection(),
            databaseManager = databaseManager
        )
        logger.info { "NamespaceRepository initialized" }
    }

    private fun createRouter(): Router {
        val router = Router.router(vertx)

        // Enable body parsing for POST/PUT requests
        router.route().handler(BodyHandler.create())

        // Health check endpoint
        val healthHandler = HealthHandler()
        router.get("/api/health").handler(healthHandler::handle)

        // OpenAPI documentation endpoints
        val openApiHandler = OpenApiHandler()
        router.get("/openapi.json").handler(openApiHandler::handle)

        val swaggerUiHandler = SwaggerUiHandler()
        router.get("/swagger").handler(swaggerUiHandler::handle)

        // Namespace management endpoints
        val namespaceHandler = NamespaceHandler(namespaceRepository)
        router.post("/api/v1/namespaces").handler { ctx ->
            launch(vertx.dispatcher()) {
                namespaceHandler.create(ctx)
            }
        }
        router.get("/api/v1/namespaces").handler { ctx ->
            launch(vertx.dispatcher()) {
                namespaceHandler.list(ctx)
            }
        }
        router.get("/api/v1/namespaces/:name").handler { ctx ->
            launch(vertx.dispatcher()) {
                namespaceHandler.get(ctx)
            }
        }
        router.delete("/api/v1/namespaces/:name").handler { ctx ->
            launch(vertx.dispatcher()) {
                namespaceHandler.delete(ctx)
            }
        }

        logger.info { "Registered routes: health, openapi, swagger, namespace CRUD" }

        return router
    }
}
