package com.example.service

import com.example.service.config.OpenTelemetryConfig
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    val serviceName = System.getProperty("otel.service.name", "ds-service")
    val otlpEndpoint = System.getProperty("otel.exporter.otlp.endpoint", "http://localhost:4317")

    logger.info { "Starting application with OpenTelemetry tracing" }
    logger.info { "Service name: $serviceName" }
    logger.info { "OTLP endpoint: $otlpEndpoint" }

    // Initialize OpenTelemetry
    val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

    // Create Vertx with OpenTelemetry tracing
    val vertxOptions = VertxOptions()
        .setTracingOptions(OpenTelemetryOptions(openTelemetry))

    val vertx = Vertx.vertx(vertxOptions)

    // Deploy the main verticle
    vertx.deployVerticle(MainVerticle())
        .onSuccess { deploymentId ->
            logger.info { "MainVerticle deployed successfully: $deploymentId" }
        }
        .onFailure { error ->
            logger.error(error) { "Failed to deploy MainVerticle" }
            vertx.close()
        }
}
