package com.example.service

import com.example.service.config.OpenTelemetryConfig
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions

/**
 * Handles application bootstrapping including OpenTelemetry initialization
 * and Vert.x instance creation with tracing enabled.
 *
 * This class separates initialization logic from the main entry point,
 * making it testable while keeping main() simple and focused on orchestration.
 */
class ApplicationBootstrapper {

    /**
     * Creates a Vert.x instance configured with OpenTelemetry tracing.
     *
     * @param serviceName The name of the service for OpenTelemetry identification
     * @param otlpEndpoint The OTLP endpoint URL for exporting traces
     * @return A configured Vert.x instance with OpenTelemetry tracing enabled
     */
    fun createVertxWithTracing(serviceName: String, otlpEndpoint: String): Vertx {
        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)
        val vertxOptions = VertxOptions()
            .setTracingOptions(OpenTelemetryOptions(openTelemetry))
        return Vertx.vertx(vertxOptions)
    }

    /**
     * Deploys the main verticle with success and failure callbacks.
     *
     * On deployment failure, automatically closes the Vert.x instance
     * to ensure proper cleanup of resources.
     *
     * @param vertx The Vert.x instance to deploy the verticle on
     * @param onSuccess Callback invoked with deployment ID on successful deployment
     * @param onFailure Callback invoked with error on deployment failure
     */
    fun deployMainVerticle(
        vertx: Vertx,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        vertx.deployVerticle(MainVerticle())
            .onSuccess(onSuccess)
            .onFailure { error ->
                onFailure(error)
                vertx.close()
            }
    }
}
