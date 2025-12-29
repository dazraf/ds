package com.example.service.handlers

import com.example.service.models.CreateNamespaceRequest
import com.example.service.models.ListNamespacesResponse
import com.example.service.models.Namespace
import com.example.service.models.NamespaceResponse
import com.example.service.repositories.NamespaceAlreadyExistsException
import com.example.service.repositories.NamespaceRepository
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val mapper = jacksonObjectMapper()

/**
 * HTTP handler for namespace management endpoints.
 *
 * Endpoints:
 * - POST   /api/v1/namespaces      - Create namespace
 * - GET    /api/v1/namespaces      - List namespaces
 * - GET    /api/v1/namespaces/:name - Get namespace by name
 * - DELETE /api/v1/namespaces/:name - Delete namespace (soft)
 */
class NamespaceHandler(
    private val namespaceRepository: NamespaceRepository
) {
    /**
     * POST /api/v1/namespaces
     *
     * Creates a new namespace (including database + migrations).
     */
    suspend fun create(ctx: RoutingContext) {
        try {
            val body = ctx.body().asJsonObject()
            val request = mapper.readValue(body.encode(), CreateNamespaceRequest::class.java)

            // TODO: Extract from JWT when authentication is implemented
            val createdBy = "system" // Placeholder

            val namespace = Namespace.create(request.name, createdBy)
            val created = namespaceRepository.create(namespace)

            val response = NamespaceResponse.from(created)

            ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())

            logger.info { "Namespace created via API: ${request.name}" }
        } catch (e: NamespaceAlreadyExistsException) {
            ctx.response()
                .setStatusCode(409)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: IllegalArgumentException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: ValueInstantiationException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Invalid request body: ${e.message}").encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to create namespace" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces
     *
     * Lists all namespaces (excluding deleted by default).
     */
    suspend fun list(ctx: RoutingContext) {
        try {
            val includeDeleted = ctx.queryParam("includeDeleted").firstOrNull()?.toBoolean() ?: false

            val namespaces = namespaceRepository.list(includeDeleted)
            val response = ListNamespacesResponse(
                namespaces = namespaces.map { NamespaceResponse.from(it) }
            )

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to list namespaces" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:name
     *
     * Gets a specific namespace by name.
     */
    suspend fun get(ctx: RoutingContext) {
        try {
            val name = ctx.pathParam("name")

            val namespace = namespaceRepository.findByName(name)

            if (namespace == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Namespace '$name' not found").encode())
                return
            }

            val response = NamespaceResponse.from(namespace)

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to get namespace" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * DELETE /api/v1/namespaces/:name
     *
     * Deletes a namespace (soft delete by default).
     */
    suspend fun delete(ctx: RoutingContext) {
        try {
            val name = ctx.pathParam("name")

            val deleted = namespaceRepository.delete(name)

            if (!deleted) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Namespace '$name' not found or already deleted").encode())
                return
            }

            ctx.response()
                .setStatusCode(204)
                .end()

            logger.info { "Namespace deleted via API: $name" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete namespace" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }
}
