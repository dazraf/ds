package com.example.service.unit.handlers

import com.example.service.handlers.OpenApiHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

class OpenApiHandlerTest : FunSpec({

    lateinit var routingContext: RoutingContext
    lateinit var response: HttpServerResponse
    lateinit var openApiHandler: OpenApiHandler

    beforeEach {
        routingContext = mockk()
        response = mockk()
        openApiHandler = OpenApiHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    test("should return 200 status code") {
        // Act
        openApiHandler.handle(routingContext)

        // Assert
        verify { response.statusCode = 200 }
    }

    test("should return application json content type") {
        // Act
        openApiHandler.handle(routingContext)

        // Assert
        verify { response.putHeader("Content-Type", "application/json") }
    }

    test("should return valid OpenAPI JSON") {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        openApiHandler.handle(routingContext)

        // Assert
        capturedJson shouldNotBe null
        val json = JsonObject(capturedJson!!)

        // Verify OpenAPI 3.0 structure
        json.getString("openapi") shouldBe "3.0.3"
        json.getJsonObject("info") shouldNotBe null
        json.getJsonObject("paths") shouldNotBe null
    }

    test("should contain health endpoint in paths") {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        openApiHandler.handle(routingContext)

        // Assert
        val json = JsonObject(capturedJson!!)
        val paths = json.getJsonObject("paths")
        paths.fieldNames() shouldContain "/api/health"
    }

    test("should contain components section with schemas") {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        openApiHandler.handle(routingContext)

        // Assert
        val json = JsonObject(capturedJson!!)
        val components = json.getJsonObject("components")
        components shouldNotBe null

        val schemas = components.getJsonObject("schemas")
        schemas shouldNotBe null
        schemas.fieldNames() shouldContain "HealthStatus"
    }
})
