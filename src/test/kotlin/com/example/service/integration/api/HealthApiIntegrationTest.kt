package com.example.service.integration.api

import com.example.service.MainVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class HealthApiIntegrationTest {

    private lateinit var webClient: WebClient
    private val testPort = 8888

    @BeforeEach
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        val config = JsonObject()
            .put("http.port", testPort)
            .put("http.host", "localhost")

        vertx.deployVerticle(
            MainVerticle(),
            DeploymentOptions().setConfig(config)
        ).onComplete(testContext.succeedingThenComplete())

        webClient = WebClient.create(vertx)
    }

    @AfterEach
    fun cleanup() {
        webClient.close()
    }

    @Test
    fun `GET health should return 200 with status OK`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        webClient.get(testPort, "localhost", "/api/health")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type"))
                        .contains("application/json")

                    val body = response.bodyAsJsonObject()
                    assertThat(body.getString("status")).isEqualTo("OK")

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `GET health should return valid JSON structure`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        webClient.get(testPort, "localhost", "/api/health")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()

                    assertThat(body.fieldNames())
                        .containsExactly("status")
                    assertThat(body.getString("status"))
                        .isNotNull()
                        .isEqualTo("OK")

                    testContext.completeNow()
                }
            })
    }
}
