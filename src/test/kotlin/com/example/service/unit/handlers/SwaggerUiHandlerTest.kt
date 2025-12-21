package com.example.service.unit.handlers

import com.example.service.handlers.SwaggerUiHandler
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SwaggerUiHandlerTest {

    @MockK
    private lateinit var routingContext: RoutingContext

    @MockK
    private lateinit var response: HttpServerResponse

    private lateinit var swaggerUiHandler: SwaggerUiHandler

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        swaggerUiHandler = SwaggerUiHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    @Test
    fun `should return 200 status code`() {
        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        verify { response.setStatusCode(200) }
    }

    @Test
    fun `should return text html content type`() {
        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        verify { response.putHeader("Content-Type", "text/html; charset=utf-8") }
    }

    @Test
    fun `should return valid HTML`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).isNotNull
        assertThat(capturedHtml).startsWith("<!DOCTYPE html>")
        assertThat(capturedHtml).contains("<html lang=\"en\">")
        assertThat(capturedHtml).contains("</html>")
    }

    @Test
    fun `should contain Swagger UI title`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).contains("<title>API Documentation - Swagger UI</title>")
    }

    @Test
    fun `should reference Swagger UI CSS`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).contains("swagger-ui-dist")
        assertThat(capturedHtml).contains("swagger-ui.css")
    }

    @Test
    fun `should reference Swagger UI JavaScript`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).contains("swagger-ui-bundle.js")
        assertThat(capturedHtml).contains("swagger-ui-standalone-preset.js")
    }

    @Test
    fun `should configure OpenAPI spec URL`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).contains("url: \"/openapi.json\"")
    }

    @Test
    fun `should contain swagger-ui div element`() {
        // Arrange
        var capturedHtml: String? = null
        every { response.end(any<String>()) } answers {
            capturedHtml = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        swaggerUiHandler.handle(routingContext)

        // Assert
        assertThat(capturedHtml).contains("<div id=\"swagger-ui\"></div>")
    }
}
