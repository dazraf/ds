package com.example.service.models

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a git-like branch.
 *
 * Branches provide a way to organize data versions similar to git branches.
 * Each namespace starts with a 'main' branch, and additional branches can be
 * created for organizing different versions of data.
 *
 * @property id Unique identifier for the branch
 * @property name Branch name (e.g., "main", "develop", "feature/xyz")
 * @property parentBranchId Optional parent branch for tracking merge lineage
 * @property createdAt Timestamp when the branch was created
 * @property createdBy User who created the branch
 */
data class Branch(
    val id: UUID,
    val name: String,
    val parentBranchId: UUID?,
    val createdAt: Instant,
    val createdBy: String
) {
    companion object {
        /**
         * Creates a new branch instance.
         *
         * @param name Branch name (must match pattern: ^[a-z0-9/_-]+$)
         * @param createdBy User creating the branch
         * @param parentBranchId Optional parent branch ID
         * @return New Branch instance
         */
        fun create(
            name: String,
            createdBy: String,
            parentBranchId: UUID? = null
        ): Branch {
            require(name.matches(Regex("^[a-z0-9/_-]+$"))) {
                "Branch name must match pattern: ^[a-z0-9/_-]+$"
            }

            return Branch(
                id = UUID.randomUUID(),
                name = name,
                parentBranchId = parentBranchId,
                createdAt = Instant.now(),
                createdBy = createdBy
            )
        }
    }
}
