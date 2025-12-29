package com.example.service.openapi.dsl

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlin.reflect.KClass

/**
 * DSL builder for OpenAPI specification using Swagger Core models
 */
class OpenApiBuilder {
    private val openApi = OpenAPI()
    private val componentSchemas = mutableMapOf<String, Schema<*>>()

    init {
        openApi.openapi = "3.0.3"
    }

    /**
     * Configure API info section (title, version, description).
     */
    fun info(block: InfoBuilder.() -> Unit) {
        openApi.info = InfoBuilder().apply(block).build()
    }

    /**
     * Define an API path with its operations (GET, POST, etc.).
     */
    fun path(path: String, block: PathBuilder.() -> Unit) {
        val pathItem = PathBuilder(this).apply(block).build()
        openApi.path(path, pathItem)
    }

    /**
     * Register a schema as a component
     */
    internal fun registerComponentSchema(name: String, schema: Schema<*>) {
        componentSchemas[name] = schema
    }

    fun build(): OpenAPI {
        // Add all registered schemas to components
        if (componentSchemas.isNotEmpty()) {
            val components = openApi.components ?: Components()
            componentSchemas.forEach { (name, schema) ->
                components.addSchemas(name, schema)
            }
            openApi.components = components
        }
        return openApi
    }
}

/**
 * Builder for API info section
 */
class InfoBuilder {
    private val info = Info()

    var title: String
        get() = info.title
        set(value) { info.title = value }

    var version: String
        get() = info.version
        set(value) { info.version = value }

    var description: String
        get() = info.description
        set(value) { info.description = value }

    fun build(): Info = info
}

/**
 * Builder for path items
 */
class PathBuilder(private val apiBuilder: OpenApiBuilder) {
    private val pathItem = PathItem()

    /**
     * Define a GET operation for this path.
     */
    fun get(block: OperationBuilder.() -> Unit) {
        pathItem.get = OperationBuilder(apiBuilder).apply(block).build()
    }

    /**
     * Define a POST operation for this path.
     */
    fun post(block: OperationBuilder.() -> Unit) {
        pathItem.post = OperationBuilder(apiBuilder).apply(block).build()
    }

    /**
     * Define a PUT operation for this path.
     */
    fun put(block: OperationBuilder.() -> Unit) {
        pathItem.put = OperationBuilder(apiBuilder).apply(block).build()
    }

    /**
     * Define a DELETE operation for this path.
     */
    fun delete(block: OperationBuilder.() -> Unit) {
        pathItem.delete = OperationBuilder(apiBuilder).apply(block).build()
    }

    /**
     * Define a PATCH operation for this path.
     */
    fun patch(block: OperationBuilder.() -> Unit) {
        pathItem.patch = OperationBuilder(apiBuilder).apply(block).build()
    }

    fun build(): PathItem = pathItem
}

/**
 * Builder for operations
 */
class OperationBuilder(private val apiBuilder: OpenApiBuilder) {
    private val operation = Operation()
    private val responses = ApiResponses()

    init {
        operation.responses = responses
    }

    var operationId: String
        get() = operation.operationId
        set(value) { operation.operationId = value }

    var summary: String
        get() = operation.summary
        set(value) { operation.summary = value }

    var description: String
        get() = operation.description
        set(value) { operation.description = value }

    /**
     * Add a tag to categorize this operation.
     */
    fun tag(tag: String) {
        operation.addTagsItem(tag)
    }

    /**
     * Define a response for this operation with the given HTTP status code.
     */
    fun response(status: String, block: ResponseBuilder.() -> Unit) {
        val response = ResponseBuilder(apiBuilder).apply(block).build()
        responses.addApiResponse(status, response)
    }

    /**
     * Define the request body for this operation.
     */
    fun requestBody(block: RequestBodyBuilder.() -> Unit) {
        operation.requestBody = RequestBodyBuilder(apiBuilder).apply(block).build()
    }

    fun build(): Operation = operation
}

/**
 * Builder for API responses
 */
class ResponseBuilder(private val apiBuilder: OpenApiBuilder) {
    private val response = ApiResponse()

    var description: String
        get() = response.description
        set(value) { response.description = value }

    /**
     * Add JSON content with schema generated from Kotlin class
     * Registers the schema as a component and uses $ref
     */
    fun <T : Any> jsonContent(type: KClass<T>, example: T? = null) {
        val typeName = type.simpleName ?: "object"

        // Generate and register the full schema as a component
        val fullSchema = generateFullSchema(type)
        apiBuilder.registerComponentSchema(typeName, fullSchema)

        // Create a reference schema (do NOT set example on $ref schema)
        val refSchema = Schema<Any>().apply {
            `$ref` = "#/components/schemas/$typeName"
        }

        // Set example on MediaType instead of schema
        val mediaType = MediaType().schema(refSchema).apply {
            example?.let { this.example = it }
        }
        val content = Content().addMediaType("application/json", mediaType)
        response.content = content
    }

    /**
     * Inline reified version for cleaner syntax
     */
    inline fun <reified T : Any> jsonContent(example: T? = null) {
        jsonContent(T::class, example)
    }

    fun build(): ApiResponse = response
}

/**
 * Builder for request body
 */
class RequestBodyBuilder(private val apiBuilder: OpenApiBuilder) {
    private val requestBody = RequestBody()

    var description: String
        get() = requestBody.description
        set(value) { requestBody.description = value }

    var required: Boolean
        get() = requestBody.required ?: false
        set(value) { requestBody.required = value }

    /**
     * Add JSON content with schema generated from Kotlin class
     * Registers the schema as a component and uses $ref
     */
    fun <T : Any> jsonContent(type: KClass<T>, example: T? = null) {
        val typeName = type.simpleName ?: "object"

        // Generate and register the full schema as a component
        val fullSchema = generateFullSchema(type)
        apiBuilder.registerComponentSchema(typeName, fullSchema)

        // Create a reference schema (do NOT set example on $ref schema)
        val refSchema = Schema<Any>().apply {
            `$ref` = "#/components/schemas/$typeName"
        }

        // Set example on MediaType instead of schema
        val mediaType = MediaType().schema(refSchema).apply {
            example?.let { this.example = it }
        }
        val content = Content().addMediaType("application/json", mediaType)
        requestBody.content = content
    }

    /**
     * Inline reified version for cleaner syntax
     */
    inline fun <reified T : Any> jsonContent(example: T? = null) {
        jsonContent(T::class, example)
    }

    fun build(): RequestBody = requestBody
}

/**
 * Generate full OpenAPI schema from Kotlin class using Swagger Core
 * This schema will be stored in components/schemas
 */
fun <T : Any> generateFullSchema(type: KClass<T>): Schema<*> {
    val schemas = ModelConverters.getInstance().readAll(type.java)

    // Get the main schema (the one matching our type name)
    val typeName = type.simpleName ?: "object"
    val mainSchema = schemas[typeName]

    // Swagger Core generates the schema correctly, just ensure type is set
    return mainSchema?.apply {
        // Force the type to be "object" for data classes
        this.type = "object"
    } ?: Schema<Any>().apply {
        this.type = "object"
        this.name = typeName
    }
}

/**
 * DSL entry point
 */
fun openApi(block: OpenApiBuilder.() -> Unit): OpenAPI {
    return OpenApiBuilder().apply(block).build()
}
