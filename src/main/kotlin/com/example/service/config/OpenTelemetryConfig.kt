package com.example.service.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object OpenTelemetryConfig {

    fun initialize(serviceName: String, otlpEndpoint: String): OpenTelemetry {
        logger.info { "Initializing OpenTelemetry with service name: $serviceName, endpoint: $otlpEndpoint" }

        val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    .put(ResourceAttributes.SERVICE_NAME, serviceName)
                    .build()
            )
        )

        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()

        logger.info { "OpenTelemetry initialized successfully" }

        return openTelemetry
    }
}
