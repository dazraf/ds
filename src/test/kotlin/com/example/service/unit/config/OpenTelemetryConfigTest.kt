package com.example.service.unit.config

import com.example.service.config.OpenTelemetryConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk

class OpenTelemetryConfigTest : FunSpec({

    test("should initialize OpenTelemetry with valid service name and endpoint") {
        val serviceName = "test-service"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

        openTelemetry shouldNotBe null
        openTelemetry.shouldBeInstanceOf<OpenTelemetry>()
    }

    test("should return OpenTelemetrySdk instance") {
        val serviceName = "test-service-sdk"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

        openTelemetry.shouldBeInstanceOf<OpenTelemetrySdk>()
    }

    test("should initialize with different service names") {
        val serviceName1 = "service-one"
        val serviceName2 = "service-two"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry1 = OpenTelemetryConfig.initialize(serviceName1, otlpEndpoint)
        val openTelemetry2 = OpenTelemetryConfig.initialize(serviceName2, otlpEndpoint)

        openTelemetry1 shouldNotBe null
        openTelemetry2 shouldNotBe null
    }

    test("should initialize with different OTLP endpoints") {
        val serviceName = "test-service"
        val endpoint1 = "http://localhost:4317"
        val endpoint2 = "http://jaeger:4317"

        val openTelemetry1 = OpenTelemetryConfig.initialize(serviceName, endpoint1)
        val openTelemetry2 = OpenTelemetryConfig.initialize(serviceName, endpoint2)

        openTelemetry1 shouldNotBe null
        openTelemetry2 shouldNotBe null
    }

    test("should initialize with custom service name") {
        val customServiceName = "my-custom-service"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(customServiceName, otlpEndpoint)

        openTelemetry shouldNotBe null
    }

    test("should initialize with remote endpoint") {
        val serviceName = "remote-service"
        val remoteEndpoint = "http://remote-jaeger.example.com:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, remoteEndpoint)

        openTelemetry shouldNotBe null
    }

    test("should create tracer provider") {
        val serviceName = "tracer-test-service"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint) as OpenTelemetrySdk

        val tracer = openTelemetry.getTracer("test-tracer")
        tracer shouldNotBe null
    }

    test("should support creating spans after initialization") {
        val serviceName = "span-test-service"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)
        val tracer = openTelemetry.getTracer("test-tracer", "1.0.0")

        val span = tracer.spanBuilder("test-span").startSpan()
        span shouldNotBe null
        span.end()
    }

    test("should initialize multiple times without error") {
        val serviceName = "multi-init-service"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry1 = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)
        val openTelemetry2 = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)
        val openTelemetry3 = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

        openTelemetry1 shouldNotBe null
        openTelemetry2 shouldNotBe null
        openTelemetry3 shouldNotBe null
    }

    test("should handle service names with special characters") {
        val serviceName = "test-service_with-special.chars"
        val otlpEndpoint = "http://localhost:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

        openTelemetry shouldNotBe null
    }

    test("should handle endpoints with different protocols") {
        val serviceName = "protocol-test-service"
        val httpsEndpoint = "https://secure-jaeger:4317"

        val openTelemetry = OpenTelemetryConfig.initialize(serviceName, httpsEndpoint)

        openTelemetry shouldNotBe null
    }

    test("should provide global tracer after initialization") {
        val serviceName = "global-tracer-service"
        val otlpEndpoint = "http://localhost:4317"

        OpenTelemetryConfig.initialize(serviceName, otlpEndpoint)

        // OpenTelemetry should still be accessible
        val openTelemetry = io.opentelemetry.api.GlobalOpenTelemetry.get()
        openTelemetry shouldNotBe null
    }
})
