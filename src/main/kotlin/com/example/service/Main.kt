package com.example.service

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Application entry point.
 *
 * Initializes OpenTelemetry tracing and deploys the main verticle.
 * Configures the service name and OTLP endpoint from system properties,
 * defaulting to "ds-service" and "http://localhost:4317" respectively.
 */
fun main() {
    val serviceName = System.getProperty("otel.service.name", "ds-service")
    val otlpEndpoint = System.getProperty("otel.exporter.otlp.endpoint", "http://localhost:4317")

    logger.info { "Starting application with OpenTelemetry tracing" }
    logger.info { "Service name: $serviceName" }
    logger.info { "OTLP endpoint: $otlpEndpoint" }

    val bootstrapper = ApplicationBootstrapper()
    val vertx = bootstrapper.createVertxWithTracing(serviceName, otlpEndpoint)

    bootstrapper.deployMainVerticle(
        vertx,
        onSuccess = { deploymentId ->
            logger.info { "MainVerticle deployed successfully: $deploymentId" }
        },
        onFailure = { error ->
            logger.error(error) { "Failed to deploy MainVerticle" }
        }
    )
}
