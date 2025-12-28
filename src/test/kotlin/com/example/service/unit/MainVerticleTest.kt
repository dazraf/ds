package com.example.service.unit

import com.example.service.MainVerticle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import java.util.concurrent.TimeUnit

class MainVerticleTest : FunSpec({

    lateinit var vertx: Vertx

    beforeEach {
        vertx = Vertx.vertx()
    }

    afterEach {
        val testContext = VertxTestContext()
        vertx.close()
            .onComplete(testContext.succeedingThenComplete())
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should deploy verticle successfully") {
        val testContext = VertxTestContext()
        val config = JsonObject().put("http.port", 8081)

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .onComplete(testContext.succeeding { deploymentId ->
                testContext.verify {
                    deploymentId shouldNotBe null
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should start HTTP server on configured port") {
        val testContext = VertxTestContext()
        val testPort = 8082
        val config = JsonObject().put("http.port", testPort)

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, testPort, "localhost", "/api/health")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    response.statusCode() shouldBe 200
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should start server on specified port") {
        val testContext = VertxTestContext()
        val config = JsonObject().put("http.port", 8086)

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config))
            .compose {
                vertx.createHttpClient()
                    .request(io.vertx.core.http.HttpMethod.GET, 8086, "localhost", "/api/health")
            }
            .compose { request ->
                request.send()
            }
            .onComplete(testContext.succeeding { response ->
                testContext.verify {
                    response.statusCode() shouldBe 200
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should register health check endpoint") {
        val testContext = VertxTestContext()
        val config = JsonObject().put("http.port", 8083)

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
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "application/json"
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should register openapi json endpoint") {
        val testContext = VertxTestContext()
        val config = JsonObject().put("http.port", 8084)

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
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "application/json"
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    test("should register swagger endpoint") {
        val testContext = VertxTestContext()
        val config = JsonObject().put("http.port", 8085)

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
                    response.statusCode() shouldBe 200
                    response.getHeader("Content-Type") shouldContain "text/html"
                    testContext.completeNow()
                }
            })

        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
})
