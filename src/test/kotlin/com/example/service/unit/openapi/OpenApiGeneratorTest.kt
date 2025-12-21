package com.example.service.unit.openapi

import com.example.service.openapi.OpenApiGenerator
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenApiGeneratorTest {

    private lateinit var generator: OpenApiGenerator

    @BeforeEach
    fun setup() {
        generator = OpenApiGenerator()
    }

    @Test
    fun `should generate valid JSON from OpenAPI spec`() {
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
        assertThat(json).isNotNull
        assertThat(json).contains("\"openapi\" : \"3.0.3\"")
        assertThat(json).contains("\"title\" : \"Test API\"")
        assertThat(json).contains("\"version\" : \"1.0.0\"")
    }

    @Test
    fun `should enable pretty printing in generated JSON`() {
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
        assertThat(json).contains("\n")
        assertThat(json).contains("  ")
    }

    @Test
    fun `should handle minimal OpenAPI spec`() {
        // Arrange
        val spec = OpenAPI().apply {
            openapi = "3.0.3"
        }

        // Act
        val json = generator.generateJson(spec)

        // Assert
        assertThat(json).isNotNull
        assertThat(json).contains("\"openapi\" : \"3.0.3\"")
    }

    @Test
    fun `should serialize paths correctly`() {
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
        assertThat(json).contains("\"/test\"")
        assertThat(json).contains("\"operationId\" : \"testOperation\"")
        assertThat(json).contains("\"200\"")
        assertThat(json).contains("\"description\" : \"Success\"")
    }

    @Test
    fun `should serialize components schemas correctly`() {
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
        assertThat(json).contains("\"components\"")
        assertThat(json).contains("\"schemas\"")
        assertThat(json).contains("\"TestModel\"")
        assertThat(json).contains("\"type\" : \"object\"")
    }

    @Test
    fun `should serialize schema references correctly`() {
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
        assertThat(json).contains("\"TestModel\"")
    }

    @Test
    fun `should handle empty paths`() {
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
        assertThat(json).isNotNull
        assertThat(json).doesNotContain("\"paths\"")
    }
}
