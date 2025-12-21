package com.example.service.unit.openapi

import com.example.service.openapi.ApiSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiSpecificationTest {

    @Test
    fun `should have HealthStatus schema in components`() {
        val spec = ApiSpecification.spec

        // Verify components section exists
        assertThat(spec.components).isNotNull

        // Verify HealthStatus schema is defined in components
        val schemas = spec.components?.schemas
        assertThat(schemas)
            .isNotNull
            .containsKey("HealthStatus")

        // Verify HealthStatus schema has correct structure
        val healthStatusSchema = schemas?.get("HealthStatus")
        assertThat(healthStatusSchema).isNotNull
        assertThat(healthStatusSchema?.type).isEqualTo("object")
        assertThat(healthStatusSchema?.properties).containsKey("status")
    }

    @Test
    fun `should use schema reference for health endpoint 200 response`() {
        val spec = ApiSpecification.spec

        // Get the health endpoint
        val healthPath = spec.paths?.get("/api/health")
        assertThat(healthPath).isNotNull

        // Get the GET operation
        val getOperation = healthPath?.get
        assertThat(getOperation).isNotNull

        // Get the 200 response
        val response200 = getOperation?.responses?.get("200")
        assertThat(response200).isNotNull

        // Get the JSON content schema
        val jsonContent = response200?.content?.get("application/json")
        assertThat(jsonContent).isNotNull

        val schema = jsonContent?.schema
        assertThat(schema).isNotNull

        // Verify it's a reference to HealthStatus
        assertThat(schema?.`$ref`).isEqualTo("#/components/schemas/HealthStatus")
    }

    @Test
    fun `should use schema reference for health endpoint 503 response`() {
        val spec = ApiSpecification.spec

        val response503 = spec.paths
            ?.get("/api/health")
            ?.get
            ?.responses
            ?.get("503")

        assertThat(response503).isNotNull

        val schema = response503?.content?.get("application/json")?.schema
        assertThat(schema?.`$ref`).isEqualTo("#/components/schemas/HealthStatus")
    }

    @Test
    fun `generated JSON should contain components and references`() {
        val json = ApiSpecification.toJson()

        // Verify the JSON contains the components section
        assertThat(json).contains("\"components\"")
        assertThat(json).contains("\"schemas\"")
        assertThat(json).contains("\"HealthStatus\"")

        // Verify the JSON contains references
        assertThat(json).contains("\"${'$'}ref\"")
        assertThat(json).contains("#/components/schemas/HealthStatus")
    }
}
