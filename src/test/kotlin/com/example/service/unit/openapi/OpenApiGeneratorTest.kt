package com.example.service.unit.openapi

import com.example.service.openapi.OpenApiGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

class OpenApiGeneratorTest : FunSpec({

    lateinit var generator: OpenApiGenerator

    beforeEach {
        generator = OpenApiGenerator()
    }

    test("should generate valid JSON from OpenAPI spec") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            info = Info().apply {
                title = "Test API"
                version = "1.0.0"
            }
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        json shouldNotBe null
        json shouldContain "\"openapi\" : \"3.0.3\""
        json shouldContain "\"title\" : \"Test API\""
        json shouldContain "\"version\" : \"1.0.0\""
    }

    test("should enable pretty printing in generated JSON") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            info = Info().apply {
                title = "Test API"
                version = "1.0.0"
            }
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        // Pretty printing means there should be line breaks and indentation
        json shouldContain "\n"
        json shouldContain "  "
    }

    test("should handle minimal OpenAPI spec") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        json shouldNotBe null
        json shouldContain "\"openapi\" : \"3.0.3\""
    }

    test("should serialize paths correctly") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            path("/test", PathItem().apply {
                get = Operation().apply {
                    operationId = "testOperation"
                    responses = ApiResponses().apply {
                        addApiResponse("200", ApiResponse().apply {
                            description = "Success"
                        })
                    }
                }
            })
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        json shouldContain "\"/test\""
        json shouldContain "\"operationId\" : \"testOperation\""
        json shouldContain "\"200\""
        json shouldContain "\"description\" : \"Success\""
    }

    test("should serialize components schemas correctly") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            components = Components().apply {
                addSchemas("TestModel", Schema<Any>().apply {
                    type = "object"
                    addProperty("id", Schema<Any>().apply {
                        type = "string"
                    })
                })
            }
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        json shouldContain "\"components\""
        json shouldContain "\"schemas\""
        json shouldContain "\"TestModel\""
        json shouldContain "\"type\" : \"object\""
    }

    test("should serialize schema references correctly") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            components = Components().apply {
                addSchemas("TestModel", Schema<Any>().apply {
                    type = "object"
                })
            }
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        // Verify that the schema is in components
        json shouldContain "\"TestModel\""
    }

    test("should handle empty paths") {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
            info = Info().apply {
                title = "Empty API"
                version = "1.0.0"
            }
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        json shouldNotBe null
        json shouldNotContain "\"paths\""
    }
})
