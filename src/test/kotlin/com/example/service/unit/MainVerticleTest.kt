package com.example.service.unit

import com.example.service.MainVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class MainVerticleTest {

    private lateinit var vertx: Vertx

    @BeforeEach
    fun setup(vertx: Vertx) {
        this.vertx = vertx
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close()
            .onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `should deploy verticle successfully`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val config = JsonObject().put("http.port", 8081)

        // Act & Assert
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .onComplete(testContext.succeeding { deploymentId ->
                testContext.verify {
                    assertThat(deploymentId).isNotNull
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should start HTTP server on configured port`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val testPort = 8082
        val config = JsonObject().put("http.port", testPort)

        // Act
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                // Verify server is listening by making a request
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, testPort, "localhost", "/api/health")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should start server on specified port`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val config = JsonObject().put("http.port", 8086)

        // Act
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                // Verify server started successfully with configured port
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, 8086, "localhost", "/api/health")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should register health check endpoint`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val config = JsonObject().put("http.port", 8083)

        // Act
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, 8083, "localhost", "/api/health")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type")).contains("application/json")
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should register openapi json endpoint`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val config = JsonObject().put("http.port", 8084)

        // Act
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, 8084, "localhost", "/openapi.json")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type")).contains("application/json")
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `should register swagger endpoint`(vertx: Vertx, testContext: VertxTestContext) {
        // Arrange
        val config = JsonObject().put("http.port", 8085)

        // Act
        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, 8085, "localhost", "/swagger")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type")).contains("text/html")
                    testContext.completeNow()
                }
            })
    }
}
