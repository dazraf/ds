package com.example.service.unit.openapi

import com.example.service.models.HealthStatus
import com.example.service.openapi.dsl.InfoBuilder
import com.example.service.openapi.dsl.OpenApiBuilder
import com.example.service.openapi.dsl.OperationBuilder
import com.example.service.openapi.dsl.PathBuilder
import com.example.service.openapi.dsl.RequestBodyBuilder
import com.example.service.openapi.dsl.ResponseBuilder
import com.example.service.openapi.dsl.generateFullSchema
import com.example.service.openapi.dsl.openApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class OpenApiDslTest : FunSpec({

    context("PathBuilder") {
        test("should support POST operation") {
            val spec = openApi {
                path("/test") {
                    post {
                        operationId = "testPost"
                        summary = "Test POST"
                        response("201") {
                            description = "Created"
                        }
                    }
                }
            }

            val postOperation = spec.paths?.get("/test")?.post
            postOperation shouldNotBe null
            postOperation?.operationId shouldBe "testPost"
            postOperation?.summary shouldBe "Test POST"
        }

        test("should support PUT operation") {
            val spec = openApi {
                path("/test") {
                    put {
                        operationId = "testPut"
                        summary = "Test PUT"
                        response("200") {
                            description = "Updated"
                        }
                    }
                }
            }

            val putOperation = spec.paths?.get("/test")?.put
            putOperation shouldNotBe null
            putOperation?.operationId shouldBe "testPut"
            putOperation?.summary shouldBe "Test PUT"
        }

        test("should support DELETE operation") {
            val spec = openApi {
                path("/test") {
                    delete {
                        operationId = "testDelete"
                        summary = "Test DELETE"
                        response("204") {
                            description = "Deleted"
                        }
                    }
                }
            }

            val deleteOperation = spec.paths?.get("/test")?.delete
            deleteOperation shouldNotBe null
            deleteOperation?.operationId shouldBe "testDelete"
            deleteOperation?.summary shouldBe "Test DELETE"
        }

        test("should support PATCH operation") {
            val spec = openApi {
                path("/test") {
                    patch {
                        operationId = "testPatch"
                        summary = "Test PATCH"
                        response("200") {
                            description = "Patched"
                        }
                    }
                }
            }

            val patchOperation = spec.paths?.get("/test")?.patch
            patchOperation shouldNotBe null
            patchOperation?.operationId shouldBe "testPatch"
            patchOperation?.summary shouldBe "Test PATCH"
        }

        test("should support multiple HTTP methods on same path") {
            val spec = openApi {
                path("/test") {
                    get {
                        operationId = "testGet"
                        response("200") { description = "OK" }
                    }
                    post {
                        operationId = "testPost"
                        response("201") { description = "Created" }
                    }
                    put {
                        operationId = "testPut"
                        response("200") { description = "Updated" }
                    }
                    delete {
                        operationId = "testDelete"
                        response("204") { description = "Deleted" }
                    }
                    patch {
                        operationId = "testPatch"
                        response("200") { description = "Patched" }
                    }
                }
            }

            val pathItem = spec.paths?.get("/test")
            pathItem?.get?.operationId shouldBe "testGet"
            pathItem?.post?.operationId shouldBe "testPost"
            pathItem?.put?.operationId shouldBe "testPut"
            pathItem?.delete?.operationId shouldBe "testDelete"
            pathItem?.patch?.operationId shouldBe "testPatch"
        }
    }

    context("RequestBodyBuilder") {
        test("should create request body with description") {
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            description = "Test request body"
                            required = true
                            jsonContent<HealthStatus>()
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            val requestBody = spec.paths?.get("/test")?.post?.requestBody
            requestBody shouldNotBe null
            requestBody?.description shouldBe "Test request body"
            requestBody?.required shouldBe true
        }

        test("should create request body with JSON content using KClass") {
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            jsonContent(HealthStatus::class)
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            val requestBody = spec.paths?.get("/test")?.post?.requestBody
            val jsonContent = requestBody?.content?.get("application/json")
            jsonContent shouldNotBe null
            jsonContent?.schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
        }

        test("should create request body with example") {
            val example = HealthStatus("healthy")
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            jsonContent(HealthStatus::class, example)
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            val requestBody = spec.paths?.get("/test")?.post?.requestBody
            val jsonContent = requestBody?.content?.get("application/json")
            jsonContent?.example shouldBe example
        }

        test("should create request body with reified type") {
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            jsonContent<HealthStatus>()
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            val requestBody = spec.paths?.get("/test")?.post?.requestBody
            val jsonContent = requestBody?.content?.get("application/json")
            jsonContent?.schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
        }

        test("should allow setting required explicitly") {
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            required = false
                            jsonContent<HealthStatus>()
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            val requestBody = spec.paths?.get("/test")?.post?.requestBody
            requestBody?.required shouldBe false
        }

        test("should allow reading and setting description") {
            val builder = RequestBodyBuilder(OpenApiBuilder())
            builder.description = "Test description"
            builder.description shouldBe "Test description"
        }

        test("should allow reading and setting required") {
            val builder = RequestBodyBuilder(OpenApiBuilder())
            builder.required = true
            builder.required shouldBe true
        }
    }

    context("InfoBuilder") {
        test("should allow reading title after setting") {
            val builder = InfoBuilder()
            builder.title = "Test API"
            builder.title shouldBe "Test API"
        }

        test("should allow reading version after setting") {
            val builder = InfoBuilder()
            builder.version = "1.0.0"
            builder.version shouldBe "1.0.0"
        }

        test("should allow reading description after setting") {
            val builder = InfoBuilder()
            builder.description = "Test description"
            builder.description shouldBe "Test description"
        }

        test("should build info with all properties") {
            val spec = openApi {
                info {
                    title = "Complete API"
                    version = "2.0.0"
                    description = "A complete API"
                }
            }

            spec.info shouldNotBe null
            spec.info?.title shouldBe "Complete API"
            spec.info?.version shouldBe "2.0.0"
            spec.info?.description shouldBe "A complete API"
        }
    }

    context("OperationBuilder") {
        test("should allow reading operationId after setting") {
            val builder = OperationBuilder(OpenApiBuilder())
            builder.operationId = "testOp"
            builder.operationId shouldBe "testOp"
        }

        test("should allow reading summary after setting") {
            val builder = OperationBuilder(OpenApiBuilder())
            builder.summary = "Test summary"
            builder.summary shouldBe "Test summary"
        }

        test("should allow reading description after setting") {
            val builder = OperationBuilder(OpenApiBuilder())
            builder.description = "Test description"
            builder.description shouldBe "Test description"
        }

        test("should support request body") {
            val spec = openApi {
                path("/test") {
                    post {
                        operationId = "createItem"
                        requestBody {
                            description = "Item to create"
                            required = true
                            jsonContent<HealthStatus>()
                        }
                        response("201") {
                            description = "Created"
                        }
                    }
                }
            }

            val operation = spec.paths?.get("/test")?.post
            operation?.requestBody shouldNotBe null
            operation?.requestBody?.description shouldBe "Item to create"
            operation?.requestBody?.required shouldBe true
        }
    }

    context("ResponseBuilder") {
        test("should allow reading description after setting") {
            val builder = ResponseBuilder(OpenApiBuilder())
            builder.description = "Test response"
            builder.description shouldBe "Test response"
        }

        test("should support reified jsonContent without example") {
            val spec = openApi {
                path("/test") {
                    get {
                        response("200") {
                            description = "Success"
                            jsonContent<HealthStatus>()
                        }
                    }
                }
            }

            val response = spec.paths?.get("/test")?.get?.responses?.get("200")
            response?.content?.get("application/json")?.schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
            response?.content?.get("application/json")?.example shouldBe null
        }

        test("should support reified jsonContent with example") {
            val example = HealthStatus("ok")
            val spec = openApi {
                path("/test") {
                    get {
                        response("200") {
                            description = "Success"
                            jsonContent(example)
                        }
                    }
                }
            }

            val response = spec.paths?.get("/test")?.get?.responses?.get("200")
            response?.content?.get("application/json")?.example shouldBe example
        }

        test("should support KClass jsonContent without example") {
            val spec = openApi {
                path("/test") {
                    get {
                        response("200") {
                            description = "Success"
                            jsonContent(HealthStatus::class)
                        }
                    }
                }
            }

            val response = spec.paths?.get("/test")?.get?.responses?.get("200")
            response?.content?.get("application/json")?.schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
        }
    }

    context("OpenApiDslKt - generateFullSchema") {
        test("should generate schema for data class") {
            val schema = generateFullSchema(HealthStatus::class)

            schema shouldNotBe null
            schema.type shouldBe "object"
            schema.properties shouldNotBe null
            schema.properties shouldContainKey "status"
        }

        test("should handle schema generation for standard classes") {
            val schema = generateFullSchema(HealthStatus::class)

            schema.shouldBeInstanceOf<io.swagger.v3.oas.models.media.Schema<*>>()
            schema.type shouldBe "object"
        }
    }

    context("OpenApiBuilder") {
        test("should create empty spec") {
            val spec = openApi { }

            spec.openapi shouldBe "3.0.3"
        }

        test("should register component schemas from responses") {
            val spec = openApi {
                path("/test") {
                    get {
                        response("200") {
                            description = "OK"
                            jsonContent<HealthStatus>()
                        }
                    }
                }
            }

            spec.components shouldNotBe null
            spec.components?.schemas!! shouldContainKey "HealthStatus"
        }

        test("should register component schemas from request bodies") {
            val spec = openApi {
                path("/test") {
                    post {
                        requestBody {
                            jsonContent<HealthStatus>()
                        }
                        response("200") {
                            description = "OK"
                        }
                    }
                }
            }

            spec.components shouldNotBe null
            spec.components?.schemas!! shouldContainKey "HealthStatus"
        }
    }
})
