package com.example.service.unit.handlers

import com.example.service.handlers.HealthHandler
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

class HealthHandlerTest {

    @MockK
    private lateinit var routingContext: RoutingContext

    @MockK
    private lateinit var response: HttpServerResponse

    private lateinit var healthHandler: HealthHandler

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        healthHandler = HealthHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    @Test
    fun `should return 200 with status OK`() {
        // Act
        healthHandler.handle(routingContext)

        // Assert
        verify { response.setStatusCode(200) }
        verify { response.putHeader("Content-Type", "application/json") }
        verify {
            response.end(match<String> { jsonString ->
                val json = JsonObject(jsonString)
                json.getString("status") == "OK"
            })
        }
    }

    @Test
    fun `should return JSON with correct structure`() {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        healthHandler.handle(routingContext)

        // Assert
        assertThat(capturedJson).isNotNull
        val json = JsonObject(capturedJson!!)
        assertThat(json.getString("status")).isEqualTo("OK")
        assertThat(json.fieldNames()).containsExactly("status")
    }
}
