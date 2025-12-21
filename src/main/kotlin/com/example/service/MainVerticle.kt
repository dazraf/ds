package com.example.service

import com.example.service.handlers.HealthHandler
import com.example.service.handlers.OpenApiHandler
import com.example.service.handlers.SwaggerUiHandler
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.tracing.TracingPolicy
import io.vertx.ext.web.Router
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class MainVerticle : AbstractVerticle() {

    override fun start(startPromise: Promise<Void>) {
        val port = config().getInteger("http.port", 8080)
        val host = config().getString("http.host", "0.0.0.0")

        val router = createRouter()
        val serverOptions = HttpServerOptions()
            .setPort(port)
            .setHost(host)
            .setTracingPolicy(TracingPolicy.ALWAYS)

        vertx.createHttpServer(serverOptions)
            .requestHandler(router)
            .listen()
            .onSuccess { server ->
                logger.info { "HTTP server started on $host:${server.actualPort()}" }
                startPromise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to start HTTP server" }
                startPromise.fail(error)
            }
    }

    private fun createRouter(): Router {
        val router = Router.router(vertx)

        // Health check endpoint
        val healthHandler = HealthHandler()
        router.get("/api/health").handler(healthHandler::handle)

        // OpenAPI documentation endpoints
        val openApiHandler = OpenApiHandler()
        router.get("/openapi.json").handler(openApiHandler::handle)

        val swaggerUiHandler = SwaggerUiHandler()
        router.get("/swagger").handler(swaggerUiHandler::handle)

        logger.info { "Registered routes: GET /api/health, GET /openapi.json, GET /swagger" }

        return router
    }
}
