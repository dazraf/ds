package com.example.service.integration.api

import com.example.service.MainVerticle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxTestContext
import java.util.concurrent.TimeUnit

class HealthApiIntegrationTest : FunSpec({

    lateinit var vertx: Vertx
    lateinit var webClient: WebClient
    val testPort = 8888

    beforeEach {
        val testContext = VertxTestContext()
        vertx = Vertx.vertx()
        val config = JsonObject()
            .put("http.port", testPort)
            .put("http.host", "localhost")

        vertx.deployVerticle(
            MainVerticle(),
            DeploymentOptions().setConfig(config)
        ).onComplete(testContext.succeedingThenComplete())

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

    test("GET health should return 200 with status OK") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/api/health")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "application/json"

                    val body = response.bodyAsJsonObject()
                    body.getString("status") shouldBe "OK"

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("GET health should return valid JSON structure") {
        val testContext = VertxTestContext()

        webClient.get(testPort, "localhost", "/api/health")
            .send()
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    val body = response.bodyAsJsonObject()

                    body.fieldNames().toList() shouldContainExactly listOf("status")
                    body.getString("status") shouldNotBe null
                    body.getString("status") shouldBe "OK"

                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
})
