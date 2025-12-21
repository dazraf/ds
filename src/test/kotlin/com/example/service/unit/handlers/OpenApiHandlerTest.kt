package com.example.service.unit.handlers

import com.example.service.handlers.OpenApiHandler
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenApiHandlerTest {

    @MockK
    private lateinit var routingContext: RoutingContext

    @MockK
    private lateinit var response: HttpServerResponse

    private lateinit var openApiHandler: OpenApiHandler

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        openApiHandler = OpenApiHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    @Test
    fun `should return 200 status code`() {
        // Act
        openApiHandler.handle(routingContext)

        // Assert
        verify { response.setStatusCode(200) }
    }

    @Test
    fun `should return application json content type`() {
        // Act
        openApiHandler.handle(routingContext)

        // Assert
        verify { response.putHeader("Content-Type", "application/json") }
    }

    @Test
    fun `should return valid OpenAPI JSON`() {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        openApiHandler.handle(routingContext)

        // Assert
        assertThat(capturedJson).isNotNull
        val json = JsonObject(capturedJson!!)

        // Verify OpenAPI 3.0 structure
        assertThat(json.getString("openapi")).isEqualTo("3.0.3")
        assertThat(json.getJsonObject("info")).isNotNull
        assertThat(json.getJsonObject("paths")).isNotNull
    }

    @Test
    fun `should contain health endpoint in paths`() {
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
        assertThat(paths.fieldNames()).contains("/api/health")
    }

    @Test
    fun `should contain components section with schemas`() {
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
        assertThat(components).isNotNull

        val schemas = components.getJsonObject("schemas")
        assertThat(schemas).isNotNull
        assertThat(schemas.fieldNames()).contains("HealthStatus")
    }
}
