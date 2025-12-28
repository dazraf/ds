package com.example.service.integration.api

import com.example.service.MainVerticle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxTestContext
import java.util.concurrent.TimeUnit

class OpenApiIntegrationTest : FunSpec({

    lateinit var vertx: Vertx
    lateinit var webClient: WebClient
    val testPort = 9090

    beforeEach {
        val testContext = VertxTestContext()
        vertx = Vertx.vertx()
        val config = JsonObject().put("http.port", testPort)

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .onComplete(testContext.succeedingThenComplete())

        webClient = WebClient.create(vertx)
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    afterEach {
        webClient.close()
        val testContext = VertxTestContext()
        vertx.close()
            .onComplete(testContext.succeedingThenComplete())
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("GET openapi json should return 200 with valid JSON") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "application/json"

                    val body = response.bodyAsJsonObject()
                    body shouldNotBe null

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("OpenAPI spec should be valid OpenAPI 3_0 specification") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()

                    // Verify OpenAPI version
                    body.getString("openapi") shouldBe "3.0.3"

                    // Verify required sections exist
                    body.getJsonObject("info") shouldNotBe null
                    body.getJsonObject("paths") shouldNotBe null

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("OpenAPI spec should contain health endpoint documentation") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()
                    val paths = body.getJsonObject("paths")

                    // Verify /api/health endpoint is documented
                    paths.containsKey("/api/health") shouldBe true

                    val healthPath = paths.getJsonObject("/api/health")
                    healthPath.containsKey("get") shouldBe true

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("OpenAPI spec should contain components with HealthStatus schema") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/openapi.json")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()
                    val components = body.getJsonObject("components")

                    components shouldNotBe null

                    val schemas = components.getJsonObject("schemas")
                    schemas shouldNotBe null
                    schemas.containsKey("HealthStatus") shouldBe true

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("OpenAPI spec should use schema references not inline definitions") {
        val testContext = VertxTestContext()

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
                    schema.getString("\$ref") shouldBe "#/components/schemas/HealthStatus"

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("GET swagger should return 200 with HTML page") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "text/html"

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("Swagger UI page should contain Swagger UI elements") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val html = response.bodyAsString()

                    html shouldContain "<!DOCTYPE html>"
                    html shouldContain "API Documentation - Swagger UI"
                    html shouldContain "swagger-ui"
                    html shouldContain "swagger-ui-bundle.js"

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("Swagger UI should be configured to load openapi json") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/swagger")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val html = response.bodyAsString()

                    // Verify Swagger UI is configured to load from /openapi.json
                    html shouldContain "url: \"/openapi.json\""

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
})
