package com.example.service.openapi

import com.example.service.models.HealthStatus
import com.example.service.openapi.dsl.openApi

/**
 * Complete OpenAPI specification for the service
 * Uses Kotlin DSL with Swagger Core for type-safe, centralized API definition
 */
object ApiSpecification {

    val spec = openApi {
        info {
            title = "Example Microservice API"
            version = "1.0.0"
            description = "Kotlin/Vert.x microservice with reactive architecture and distributed tracing"
        }

        path("/api/health") {
            get {
                operationId = "getHealth"
                summary = "Health check endpoint"
                description = "Returns the current health status of the service"
                tag("Health")

                response("200") {
                    description = "Service is healthy and operational"
                    jsonContent<HealthStatus>(
                        example = HealthStatus(status = "OK")
                    )
                }

                response("503") {
                    description = "Service is unavailable or unhealthy"
                    jsonContent<HealthStatus>(
                        example = HealthStatus(status = "UNAVAILABLE")
                    )
                }
            }
        }

        path("/openapi.json") {
            get {
                operationId = "getOpenApiSpec"
                summary = "Get OpenAPI specification"
                description = "Returns the complete OpenAPI 3.0 specification for this API in JSON format. " +
                        "This specification is generated at runtime from the Kotlin DSL definition."
                tag("Documentation")

                response("200") {
                    description = "OpenAPI 3.0 specification in JSON format"
                }
            }
        }

        path("/swagger") {
            get {
                operationId = "getSwaggerUI"
                summary = "Swagger UI interface"
                description = "Interactive API documentation and testing interface powered by Swagger UI. " +
                        "Allows you to explore and test all API endpoints directly from your browser."
                tag("Documentation")

                response("200") {
                    description = "Swagger UI HTML page"
                }
            }
        }
    }

    /**
     * Generate OpenAPI JSON using Swagger Core
     */
    fun toJson(): String {
        return OpenApiGenerator().generateJson(spec)
    }
}
