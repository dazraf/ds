package com.example.service.unit.openapi

import com.example.service.openapi.ApiSpecification
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class ApiSpecificationTest : FunSpec({

    test("should have HealthStatus schema in components") {
        val spec = ApiSpecification.spec

        // Verify components section exists
        spec.components shouldNotBe null

        // Verify HealthStatus schema is defined in components
        val schemas = spec.components?.schemas
        schemas shouldNotBe null
        schemas!! shouldContainKey "HealthStatus"

        // Verify HealthStatus schema has correct structure
        val healthStatusSchema = schemas["HealthStatus"]
        healthStatusSchema shouldNotBe null
        healthStatusSchema?.type shouldBe "object"
        healthStatusSchema?.properties!! shouldContainKey "status"
    }

    test("should use schema reference for health endpoint 200 response") {
        val spec = ApiSpecification.spec

        // Get the health endpoint
        val healthPath = spec.paths?.get("/api/health")
        healthPath shouldNotBe null

        // Get the GET operation
        val getOperation = healthPath?.get
        getOperation shouldNotBe null

        // Get the 200 response
        val response200 = getOperation?.responses?.get("200")
        response200 shouldNotBe null

        // Get the JSON content schema
        val jsonContent = response200?.content?.get("application/json")
        jsonContent shouldNotBe null

        val schema = jsonContent?.schema
        schema shouldNotBe null

        // Verify it's a reference to HealthStatus
        schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
    }

    test("should use schema reference for health endpoint 503 response") {
        val spec = ApiSpecification.spec

        val response503 = spec.paths
            ?.get("/api/health")
            ?.get
            ?.responses
            ?.get("503")

        response503 shouldNotBe null

        val schema = response503?.content?.get("application/json")?.schema
        schema?.`$ref` shouldBe "#/components/schemas/HealthStatus"
    }

    test("generated JSON should contain components and references") {
        val json = ApiSpecification.toJson()

        // Verify the JSON contains the components section
        json shouldContain "\"components\""
        json shouldContain "\"schemas\""
        json shouldContain "\"HealthStatus\""

        // Verify the JSON contains references
        json shouldContain "\"${'$'}ref\""
        json shouldContain "#/components/schemas/HealthStatus"
    }
})
