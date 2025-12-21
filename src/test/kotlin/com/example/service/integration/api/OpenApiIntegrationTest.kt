package com.example.service.integration.api

import com.example.service.MainVerticle
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
class OpenApiIntegrationTest {

    private lateinit var webClient: WebClient
    private val testPort = 9090

    @BeforeEach
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        val config = JsonObject().put("http.port", testPort)

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .onComplete(testContext.succeedingThenComplete())

        webClient = WebClient.create(vertx)
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close()
            .onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `GET openapi json should return 200 with valid JSON`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type")).contains("application/json")

                    val body = response.bodyAsJsonObject()
                    assertThat(body).isNotNull

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `OpenAPI spec should be valid OpenAPI 3_0 specification`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()

                    // Verify OpenAPI version
                    assertThat(body.getString("openapi")).isEqualTo("3.0.3")

                    // Verify required sections exist
                    assertThat(body.getJsonObject("info")).isNotNull
                    assertThat(body.getJsonObject("paths")).isNotNull

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `OpenAPI spec should contain health endpoint documentation`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()
                    val paths = body.getJsonObject("paths")

                    // Verify /api/health endpoint is documented
                    assertThat(paths.containsKey("/api/health")).isTrue()

                    val healthPath = paths.getJsonObject("/api/health")
                    assertThat(healthPath.containsKey("get")).isTrue()

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `OpenAPI spec should contain components with HealthStatus schema`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()
                    val components = body.getJsonObject("components")

                    assertThat(components).isNotNull

                    val schemas = components.getJsonObject("schemas")
                    assertThat(schemas).isNotNull
                    assertThat(schemas.containsKey("HealthStatus")).isTrue()

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `OpenAPI spec should use schema references not inline definitions`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()
                    val paths = body.getJsonObject("paths")
                    val healthPath = paths.getJsonObject("/api/health")
                    val getOperation = healthPath.getJsonObject("get")
                    val responses = getOperation.getJsonObject("responses")
                    val response200 = responses.getJsonObject("200")
                    val content = response200.getJsonObject("content")
                    val jsonContent = content.getJsonObject("application/json")
                    val schema = jsonContent.getJsonObject("schema")

                    // Verify schema uses $ref
                    assertThat(schema.getString("\$ref")).isEqualTo("#/components/schemas/HealthStatus")

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `GET swagger should return 200 with HTML page`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    assertThat(response.statusCode()).isEqualTo(200)
                    assertThat(response.getHeader("Content-Type")).contains("text/html")

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `Swagger UI page should contain Swagger UI elements`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val html = response.bodyAsString()

                    assertThat(html).contains("<!DOCTYPE html>")
                    assertThat(html).contains("API Documentation - Swagger UI")
                    assertThat(html).contains("swagger-ui")
                    assertThat(html).contains("swagger-ui-bundle.js")

                    testContext.completeNow()
                }
            })
    }

    @Test
    fun `Swagger UI should be configured to load openapi json`(vertx: Vertx, testContext: VertxTestContext) {
        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val html = response.bodyAsString()

                    // Verify Swagger UI is configured to load from /openapi.json
                    assertThat(html).contains("url: \"/openapi.json\"")

                    testContext.completeNow()
                }
            })
    }
}
