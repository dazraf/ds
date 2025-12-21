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
    }

    /**
     * Generate OpenAPI JSON using Swagger Core
     */
    fun toJson(): String {
        return OpenApiGenerator().generateJson(spec)
    }
}
