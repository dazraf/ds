package com.example.service.unit.handlers

import com.example.service.handlers.HealthHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

class HealthHandlerTest : FunSpec({

    lateinit var routingContext: RoutingContext
    lateinit var response: HttpServerResponse
    lateinit var healthHandler: HealthHandler

    beforeEach {
        routingContext = mockk()
        response = mockk()
        healthHandler = HealthHandler()

        every { routingContext.response() } returns response
        every { response.putHeader(any<String>(), any<String>()) } returns response
        every { response.setStatusCode(any()) } returns response
        every { response.end(any<String>()) } returns Future.succeededFuture()
    }

    test("should return 200 with status OK") {
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

    test("should return JSON with correct structure") {
        // Arrange
        var capturedJson: String? = null
        every { response.end(any<String>()) } answers {
            capturedJson = firstArg<String>()
            Future.succeededFuture<Void>()
        }

        // Act
        healthHandler.handle(routingContext)

        // Assert
        capturedJson shouldNotBe null
        val json = JsonObject(capturedJson!!)
        json.getString("status") shouldBe "OK"
        json.fieldNames().toList() shouldBe listOf("status")
    }
})
