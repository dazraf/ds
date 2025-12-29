package com.example.service.models

import java.time.Instant
import java.util.UUID

/**
 * Domain model representing a namespace in the registry database.
 *
 * Each namespace corresponds to a separate PostgreSQL database containing
 * bitemporal data, branches, and tags for that namespace.
 */
data class Namespace(
    val id: UUID,
    val name: String,
    val databaseName: String,
    val createdAt: Instant,
    val createdBy: String,
    val status: NamespaceStatus
) {
    companion object {
        /**
         * Creates a new Namespace from request data.
         */
        fun create(name: String, createdBy: String): Namespace {
            val databaseName = "ds_ns_${name.replace("-", "_")}"
            return Namespace(
                id = UUID.randomUUID(),
                name = name,
                databaseName = databaseName,
                createdAt = Instant.now(),
                createdBy = createdBy,
                status = NamespaceStatus.ACTIVE
            )
        }
    }
}

/**
 * Namespace lifecycle status.
 */
enum class NamespaceStatus {
    /** Namespace is active and operational */
    ACTIVE,

    /** Namespace is temporarily suspended (no operations allowed) */
    SUSPENDED,

    /** Namespace is marked as deleted (soft delete) */
    DELETED
}
