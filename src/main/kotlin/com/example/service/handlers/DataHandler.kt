package com.example.service.handlers

import com.example.service.models.AddTagsRequest
import com.example.service.models.DataEntry
import com.example.service.models.DataEntryHistoryResponse
import com.example.service.models.DataEntryMetadataResponse
import com.example.service.models.ListDataEntriesResponse
import com.example.service.models.Tag
import com.example.service.models.TagsResponse
import com.example.service.models.UploadDataRequest
import com.example.service.repositories.BranchRepository
import com.example.service.repositories.DataEntryRepository
import com.example.service.repositories.NamespaceRepository
import com.example.service.repositories.TagAlreadyExistsException
import com.example.service.repositories.TagRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import mu.KotlinLogging
import java.time.Instant
import java.time.format.DateTimeParseException

private val logger = KotlinLogging.logger {}
private val mapper = jacksonObjectMapper()

/**
 * HTTP handler for data entry management endpoints.
 *
 * Endpoints:
 * - POST   /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name - Upload data
 * - GET    /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name - Download data
 * - GET    /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/metadata - Get metadata
 * - GET    /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/history - Get history
 * - GET    /api/v1/namespaces/:namespace/branches/:branch/data/:dataType - List entries by type
 * - DELETE /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name - Delete entry
 * - POST   /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags - Add tags
 * - GET    /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags - Get tags
 * - DELETE /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags/:tag - Delete tag
 */
class DataHandler(
    private val namespaceRepository: NamespaceRepository
) {
    /**
     * POST /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name
     *
     * Uploads data with multipart/form-data.
     * - Part "file": binary data
     * - Part "metadata": JSON with validFrom, validTo, mediaType, tags
     */
    suspend fun upload(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Get file upload
            val fileUploads = ctx.fileUploads()
            val fileUpload = fileUploads.firstOrNull { it.name() == "file" }
            if (fileUpload == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Missing 'file' part in multipart upload").encode())
                return
            }

            // Read file data
            val data = ctx.vertx().fileSystem().readFile(fileUpload.uploadedFileName()).coAwait()
            val dataBytes = data.bytes

            // Parse metadata (optional)
            val metadataJson = ctx.request().getFormAttribute("metadata")
            val metadata = if (metadataJson != null) {
                mapper.readValue(metadataJson, UploadDataRequest::class.java)
            } else {
                UploadDataRequest()
            }

            // TODO: Extract from JWT when authentication is implemented
            val createdBy = "system" // Placeholder

            // Determine media type
            val mediaType = metadata.mediaType
                ?: fileUpload.contentType()
                ?: "application/octet-stream"

            // Create data entry
            val entry = DataEntry.create(
                branchId = branch.id,
                dataType = dataType,
                name = name,
                validFrom = metadata.validFrom ?: Instant.now(),
                validTo = metadata.validTo,
                data = dataBytes,
                mediaType = mediaType,
                createdBy = createdBy
            )

            val dataEntryRepository = DataEntryRepository(pool)
            val created = dataEntryRepository.create(entry)

            // Add tags if provided
            val tagRepository = TagRepository(pool)
            val createdTags = mutableListOf<Tag>()
            for (tagValue in metadata.tags) {
                try {
                    val tag = Tag.create(created.id, tagValue)
                    val createdTag = tagRepository.create(tag)
                    createdTags.add(createdTag)
                } catch (e: TagAlreadyExistsException) {
                    // Tag already exists, skip
                    logger.debug { "Tag '$tagValue' already exists for data entry ${created.id}" }
                }
            }

            // Return metadata response
            val response = DataEntryMetadataResponse.from(created, createdTags)

            ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())

            logger.info { "Data uploaded via API: $namespace/$branchName/$dataType/$name" }
        } catch (e: IllegalArgumentException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to upload data" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name
     *
     * Downloads data (binary response).
     * Query parameters:
     * - validTimeAsOf: ISO-8601 timestamp (default: now)
     * - transactionTimeAsOf: ISO-8601 timestamp (default: now)
     */
    suspend fun download(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Parse temporal query parameters
            val validTimeAsOf = parseInstantParam(ctx, "validTimeAsOf") ?: Instant.now()
            val transactionTimeAsOf = parseInstantParam(ctx, "transactionTimeAsOf") ?: Instant.now()

            // Find data entry
            val dataEntryRepository = DataEntryRepository(pool)
            val entry = dataEntryRepository.findBitemporal(
                branchId = branch.id,
                dataType = dataType,
                name = name,
                validTimeAsOf = validTimeAsOf,
                transactionTimeAsOf = transactionTimeAsOf
            )

            if (entry == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found").encode())
                return
            }

            // Return binary data
            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", entry.mediaType)
                .putHeader("Content-Length", entry.sizeBytes.toString())
                .putHeader("Content-Disposition", "attachment; filename=\"$name\"")
                .end(Buffer.buffer(entry.data))

            logger.info { "Data downloaded via API: $namespace/$branchName/$dataType/$name" }
        } catch (e: IllegalArgumentException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to download data" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/metadata
     *
     * Gets metadata for a data entry (without binary data).
     * Query parameters:
     * - validTimeAsOf: ISO-8601 timestamp (default: now)
     * - transactionTimeAsOf: ISO-8601 timestamp (default: now)
     */
    suspend fun getMetadata(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Parse temporal query parameters
            val validTimeAsOf = parseInstantParam(ctx, "validTimeAsOf") ?: Instant.now()
            val transactionTimeAsOf = parseInstantParam(ctx, "transactionTimeAsOf") ?: Instant.now()

            // Find data entry
            val dataEntryRepository = DataEntryRepository(pool)
            val entry = dataEntryRepository.findBitemporal(
                branchId = branch.id,
                dataType = dataType,
                name = name,
                validTimeAsOf = validTimeAsOf,
                transactionTimeAsOf = transactionTimeAsOf
            )

            if (entry == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found").encode())
                return
            }

            // Get tags
            val tagRepository = TagRepository(pool)
            val tags = tagRepository.findByDataEntryId(entry.id)

            val response = DataEntryMetadataResponse.from(entry, tags)

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: IllegalArgumentException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to get metadata" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/history
     *
     * Gets the full version history for a data entry.
     */
    suspend fun getHistory(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Get history
            val dataEntryRepository = DataEntryRepository(pool)
            val history = dataEntryRepository.getHistory(branch.id, dataType, name)

            // Get tags for each entry
            val tagRepository = TagRepository(pool)
            val historyWithTags = history.map { entry ->
                val tags = tagRepository.findByDataEntryId(entry.id)
                DataEntryMetadataResponse.from(entry, tags)
            }

            val response = DataEntryHistoryResponse(historyWithTags)

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to get history" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:namespace/branches/:branch/data/:dataType
     *
     * Lists all current data entries for a given type.
     * Query parameters:
     * - tag: Filter by tag value
     */
    suspend fun list(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            val dataEntryRepository = DataEntryRepository(pool)
            val tagRepository = TagRepository(pool)

            // Check for tag filter
            val tagFilter = ctx.queryParam("tag").firstOrNull()

            val entries = if (tagFilter != null) {
                // Find entries by tag
                val entryIds = tagRepository.findDataEntriesByTag(tagFilter)
                // Filter by current entries in this branch/type
                val currentEntries = dataEntryRepository.listCurrent(branch.id, dataType)
                currentEntries.filter { it.id in entryIds }
            } else {
                // List all current entries
                dataEntryRepository.listCurrent(branch.id, dataType)
            }

            // Get tags for each entry
            val entriesWithTags = entries.map { entry ->
                val tags = tagRepository.findByDataEntryId(entry.id)
                DataEntryMetadataResponse.from(entry, tags)
            }

            val response = ListDataEntriesResponse(entriesWithTags)

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to list data entries" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * DELETE /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name
     *
     * Deletes (versions) a data entry.
     */
    suspend fun delete(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Delete entry
            val dataEntryRepository = DataEntryRepository(pool)
            val deleted = dataEntryRepository.delete(branch.id, dataType, name)

            if (!deleted) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found or already deleted").encode())
                return
            }

            ctx.response()
                .setStatusCode(204)
                .end()

            logger.info { "Data deleted via API: $namespace/$branchName/$dataType/$name" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete data" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * POST /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags
     *
     * Adds tags to a data entry.
     */
    suspend fun addTags(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Parse request body
            val body = ctx.body().asJsonObject()
            val request = mapper.readValue(body.encode(), AddTagsRequest::class.java)

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Find current entry
            val dataEntryRepository = DataEntryRepository(pool)
            val entry = dataEntryRepository.findCurrent(branch.id, dataType, name)
            if (entry == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found").encode())
                return
            }

            // Add tags
            val tagRepository = TagRepository(pool)
            val createdTags = mutableListOf<Tag>()
            for (tagValue in request.tags) {
                try {
                    val tag = Tag.create(entry.id, tagValue)
                    val createdTag = tagRepository.create(tag)
                    createdTags.add(createdTag)
                } catch (e: TagAlreadyExistsException) {
                    // Tag already exists, skip
                    logger.debug { "Tag '$tagValue' already exists for data entry ${entry.id}" }
                }
            }

            // Get all current tags
            val allTags = tagRepository.findByDataEntryId(entry.id)
            val response = TagsResponse(allTags.map { it.value }.sorted())

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())

            logger.info { "Tags added via API: $namespace/$branchName/$dataType/$name" }
        } catch (e: IllegalArgumentException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", e.message).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to add tags" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * GET /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags
     *
     * Gets all tags for a data entry.
     */
    suspend fun getTags(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Find current entry
            val dataEntryRepository = DataEntryRepository(pool)
            val entry = dataEntryRepository.findCurrent(branch.id, dataType, name)
            if (entry == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found").encode())
                return
            }

            // Get tags
            val tagRepository = TagRepository(pool)
            val tags = tagRepository.findByDataEntryId(entry.id)
            val response = TagsResponse(tags.map { it.value }.sorted())

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode())
        } catch (e: Exception) {
            logger.error(e) { "Failed to get tags" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * DELETE /api/v1/namespaces/:namespace/branches/:branch/data/:dataType/:name/tags/:tag
     *
     * Deletes a specific tag from a data entry.
     */
    suspend fun deleteTag(ctx: RoutingContext) {
        try {
            val namespace = ctx.pathParam("namespace")
            val branchName = ctx.pathParam("branch")
            val dataType = ctx.pathParam("dataType")
            val name = ctx.pathParam("name")
            val tag = ctx.pathParam("tag")

            // Get namespace database pool
            val pool = getNamespacePool(ctx, namespace) ?: return

            // Get branch ID
            val branchRepository = BranchRepository(pool)
            val branch = branchRepository.findByName(branchName)
            if (branch == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Branch '$branchName' not found").encode())
                return
            }

            // Find current entry
            val dataEntryRepository = DataEntryRepository(pool)
            val entry = dataEntryRepository.findCurrent(branch.id, dataType, name)
            if (entry == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Data entry not found").encode())
                return
            }

            // Delete tag
            val tagRepository = TagRepository(pool)
            val deleted = tagRepository.delete(entry.id, tag)

            if (!deleted) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("error", "Tag not found").encode())
                return
            }

            ctx.response()
                .setStatusCode(204)
                .end()

            logger.info { "Tag deleted via API: $namespace/$branchName/$dataType/$name - tag: $tag" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete tag" }
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Internal server error").encode())
        }
    }

    /**
     * Helper function to get namespace database pool.
     * Returns null and sends error response if namespace not found.
     */
    private suspend fun getNamespacePool(ctx: RoutingContext, namespace: String): Pool? {
        val ns = namespaceRepository.findByName(namespace)
        if (ns == null) {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Namespace '$namespace' not found").encode())
            return null
        }

        return namespaceRepository.getPool(namespace)
    }

    /**
     * Helper function to parse ISO-8601 timestamp from query parameter.
     */
    private fun parseInstantParam(ctx: RoutingContext, paramName: String): Instant? {
        val param = ctx.queryParam(paramName).firstOrNull() ?: return null
        return try {
            Instant.parse(param)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid timestamp format for '$paramName': $param. Expected ISO-8601 format.")
        }
    }
}
