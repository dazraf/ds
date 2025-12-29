package com.example.service.unit.handlers

import com.example.service.handlers.SwaggerUiHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext

class SwaggerUiHandlerTest : FunSpec({

    lateinit var routingContext: RoutingContext
    lateinit var response: HttpServerResponse
    lateinit var swaggerUiHandler: SwaggerUiHandler

    beforeEach {
        routingContext = mockk()
        response = mockk()
        swaggerUiHandler = SwaggerUiHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    test("should return 200 status code") {
        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        verify { response.statusCode = 200 }
    }

    test("should return text html content type") {
        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        verify { response.putHeader("Content-Type", "text/html; charset=utf-8") }
    }

    test("should return valid HTML") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldNotBe null
        capturedHtml!! shouldStartWith "<!DOCTYPE html>"
        capturedHtml shouldContain "<html lang=\"en\">"
        capturedHtml shouldContain "</html>"
    }

    test("should contain Swagger UI title") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldContain "<title>API Documentation - Swagger UI</title>"
    }

    test("should reference Swagger UI CSS") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldContain "swagger-ui-dist"
        capturedHtml shouldContain "swagger-ui.css"
    }

    test("should reference Swagger UI JavaScript") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldContain "swagger-ui-bundle.js"
        capturedHtml shouldContain "swagger-ui-standalone-preset.js"
    }

    test("should configure OpenAPI spec URL") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldContain "url: \"/openapi.json\""
    }

    test("should contain swagger-ui div element") {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        capturedHtml shouldContain "<div id=\"swagger-ui\"></div>"
    }
})
