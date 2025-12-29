package com.example.service.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * Request DTO for creating a new namespace.
 */
data class CreateNamespaceRequest(
    @field:JsonProperty("name")
    val name: String
) {
    init {
        require(name.matches(Regex("^[a-z0-9-]+$"))) {
            "Namespace name must contain only lowercase letters, numbers, and hyphens"
        }
        require(name.length in 1..63) {
            "Namespace name must be between 1 and 63 characters"
        }
    }
}

/**
 * Response DTO for namespace operations.
 */
data class NamespaceResponse(
    @JsonProperty("id")
    val id: UUID,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("databaseName")
    val databaseName: String,

    @JsonProperty("createdAt")
    val createdAt: Instant,

    @JsonProperty("createdBy")
    val createdBy: String,

    @JsonProperty("status")
    val status: String
) {
    companion object {
        fun from(namespace: Namespace): NamespaceResponse {
            return NamespaceResponse(
                id = namespace.id,
                name = namespace.name,
                databaseName = namespace.databaseName,
                createdAt = namespace.createdAt,
                createdBy = namespace.createdBy,
                status = namespace.status.name.lowercase()
            )
        }
    }
}

/**
 * Response DTO for listing namespaces.
 */
data class ListNamespacesResponse(
    @JsonProperty("namespaces")
    val namespaces: List<NamespaceResponse>
)
