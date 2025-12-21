package com.example.service.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI

/**
 * Generates OpenAPI 3.0 JSON specification using Swagger Core
 */
class OpenApiGenerator {

    private val objectMapper: ObjectMapper = Json.mapper().apply {
        // Use Swagger's configured mapper but enable pretty printing
        enable(SerializationFeature.INDENT_OUTPUT)
        registerKotlinModule()
    }

    /**
     * Generate OpenAPI JSON from Swagger Core OpenAPI model
     */
    fun generateJson(spec: OpenAPI): String {
        return objectMapper.writeValueAsString(spec)
    }
}
