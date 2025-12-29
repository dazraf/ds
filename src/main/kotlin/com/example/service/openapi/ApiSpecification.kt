package com.example.service.openapi

import com.example.service.models.AddTagsRequest
import com.example.service.models.CreateNamespaceRequest
import com.example.service.models.DataEntryHistoryResponse
import com.example.service.models.DataEntryMetadataResponse
import com.example.service.models.HealthStatus
import com.example.service.models.ListDataEntriesResponse
import com.example.service.models.ListNamespacesResponse
import com.example.service.models.NamespaceResponse
import com.example.service.models.TagsResponse
import com.example.service.openapi.dsl.openApi

/**
 * Complete OpenAPI specification for the service
 * Uses Kotlin DSL with Swagger Core for type-safe, centralized API definition
 */
object ApiSpecification {

    val spec = openApi {
        info {
            title = "DS - Bitemporal Data API"
            version = "1.0.0"
            description = "A bitemporal data management API with git-like branching semantics, " +
                    "multipart file upload, and comprehensive version history tracking. " +
                    "Built with Kotlin, Vert.x, and PostgreSQL."
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

        // Namespace Management Endpoints
        path("/api/v1/namespaces") {
            post {
                operationId = "createNamespace"
                summary = "Create a new namespace"
                description = "Creates a new namespace with its own isolated PostgreSQL database. " +
                        "Each namespace gets a database named 'ds_ns_<namespace-name>' where all data is stored. " +
                        "Migrations are automatically run on the new database."
                tag("Namespaces")

                requestBody {
                    description = "Namespace creation request"
                    jsonContent<CreateNamespaceRequest>(
                        example = CreateNamespaceRequest(name = "my-project")
                    )
                }

                response("201") {
                    description = "Namespace created successfully"
                    jsonContent<NamespaceResponse>()
                }

                response("400") {
                    description = "Invalid namespace name (must be lowercase alphanumeric with hyphens)"
                }

                response("409") {
                    description = "Namespace with this name already exists"
                }
            }

            get {
                operationId = "listNamespaces"
                summary = "List all namespaces"
                description = "Returns a list of all namespaces, optionally including deleted ones. " +
                        "By default, only active namespaces are returned."
                tag("Namespaces")

                response("200") {
                    description = "List of namespaces"
                    jsonContent<ListNamespacesResponse>()
                }
            }
        }

        path("/api/v1/namespaces/{name}") {
            get {
                operationId = "getNamespace"
                summary = "Get namespace by name"
                description = "Retrieves detailed information about a specific namespace"
                tag("Namespaces")

                response("200") {
                    description = "Namespace found"
                    jsonContent<NamespaceResponse>()
                }

                response("404") {
                    description = "Namespace not found"
                }
            }

            delete {
                operationId = "deleteNamespace"
                summary = "Delete a namespace (soft delete)"
                description = "Marks a namespace as deleted. The database is not dropped. " +
                        "Use the permanentlyDelete operation to drop the database."
                tag("Namespaces")

                response("204") {
                    description = "Namespace deleted successfully"
                }

                response("404") {
                    description = "Namespace not found or already deleted"
                }
            }
        }

        // Data Upload Endpoint
        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}/{name}") {
            post {
                operationId = "uploadData"
                summary = "Upload data with multipart form"
                description = "Uploads binary data using multipart/form-data. " +
                        "Supports automatic versioning - if data with the same name exists, it will be versioned. " +
                        "Accepts optional metadata including tags, validFrom/validTo timestamps."
                tag("Data")

                response("201") {
                    description = "Data uploaded successfully"
                    jsonContent<DataEntryMetadataResponse>()
                }

                response("404") {
                    description = "Namespace or branch not found"
                }

                response("400") {
                    description = "Missing file part or invalid request"
                }
            }

            get {
                operationId = "downloadData"
                summary = "Download binary data"
                description = "Downloads the binary data for a specific entry. " +
                        "Supports temporal queries via query parameters: validTimeAsOf and transactionTimeAsOf (ISO-8601 format). " +
                        "Returns binary data with appropriate Content-Type header."
                tag("Data")

                response("200") {
                    description = "Binary data download"
                }

                response("404") {
                    description = "Data entry not found"
                }
            }

            delete {
                operationId = "deleteData"
                summary = "Delete data entry (soft delete)"
                description = "Soft deletes a data entry by setting its transaction_to timestamp. " +
                        "The entry remains in history and can be queried with transaction time parameters."
                tag("Data")

                response("204") {
                    description = "Data deleted successfully"
                }

                response("404") {
                    description = "Data entry not found or already deleted"
                }
            }
        }

        // Data Metadata Endpoint
        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}/{name}/metadata") {
            get {
                operationId = "getDataMetadata"
                summary = "Get data entry metadata"
                description = "Returns metadata for a data entry without the binary data. " +
                        "Includes size, media type, timestamps, and tags. " +
                        "Supports temporal queries via validTimeAsOf and transactionTimeAsOf parameters."
                tag("Data")

                response("200") {
                    description = "Metadata retrieved successfully"
                    jsonContent<DataEntryMetadataResponse>()
                }

                response("404") {
                    description = "Data entry not found"
                }
            }
        }

        // Data History Endpoint
        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}/{name}/history") {
            get {
                operationId = "getDataHistory"
                summary = "Get version history"
                description = "Returns the complete version history for a data entry, " +
                        "ordered by transaction time descending (most recent first). " +
                        "Each version includes all metadata and tags."
                tag("Data")

                response("200") {
                    description = "History retrieved successfully"
                    jsonContent<DataEntryHistoryResponse>()
                }
            }
        }

        // Data List Endpoint
        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}") {
            get {
                operationId = "listData"
                summary = "List data entries by type"
                description = "Lists all current data entries of a specific type. " +
                        "Supports filtering by tag using the 'tag' query parameter. " +
                        "Returns metadata for each entry including tags."
                tag("Data")

                response("200") {
                    description = "List of data entries"
                    jsonContent<ListDataEntriesResponse>()
                }

                response("404") {
                    description = "Namespace or branch not found"
                }
            }
        }

        // Tag Management Endpoints
        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}/{name}/tags") {
            post {
                operationId = "addTags"
                summary = "Add tags to data entry"
                description = "Adds one or more tags to a data entry. " +
                        "Duplicate tags are silently ignored. " +
                        "Returns the complete list of tags after addition."
                tag("Tags")

                requestBody {
                    description = "Tags to add"
                    jsonContent<AddTagsRequest>(
                        example = AddTagsRequest(tags = listOf("production", "v1.0"))
                    )
                }

                response("200") {
                    description = "Tags added successfully"
                    jsonContent<TagsResponse>()
                }

                response("404") {
                    description = "Data entry not found"
                }

                response("400") {
                    description = "Invalid request (empty tags, tag too long, etc.)"
                }
            }

            get {
                operationId = "getTags"
                summary = "Get all tags for data entry"
                description = "Returns all tags associated with a data entry, sorted alphabetically"
                tag("Tags")

                response("200") {
                    description = "Tags retrieved successfully"
                    jsonContent<TagsResponse>()
                }

                response("404") {
                    description = "Data entry not found"
                }
            }
        }

        path("/api/v1/namespaces/{namespace}/branches/{branch}/data/{dataType}/{name}/tags/{tag}") {
            delete {
                operationId = "deleteTag"
                summary = "Delete a specific tag"
                description = "Removes a specific tag from a data entry"
                tag("Tags")

                response("204") {
                    description = "Tag deleted successfully"
                }

                response("404") {
                    description = "Tag or data entry not found"
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
