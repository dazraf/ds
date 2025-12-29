package com.example.service.repositories

import com.example.service.models.Branch
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Repository for managing branches within a namespace database.
 *
 * Provides CRUD operations for git-like branches that organize data versions.
 * Each namespace database has its own set of branches, starting with a 'main' branch.
 *
 * @property pool Connection pool to the namespace database
 */
class BranchRepository(
    private val pool: Pool
) {
    /**
     * Creates a new branch.
     *
     * @param branch The branch to create
     * @return The created branch
     * @throws BranchAlreadyExistsException if a branch with the same name already exists
     */
    suspend fun create(branch: Branch): Branch {
        val query = """
            INSERT INTO branches (id, name, parent_branch_id, created_at, created_by)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING id, name, parent_branch_id, created_at, created_by
        """.trimIndent()

        try {
            val result = pool.preparedQuery(query)
                .execute(
                    Tuple.of(
                        branch.id,
                        branch.name,
                        branch.parentBranchId,
                        OffsetDateTime.ofInstant(branch.createdAt, ZoneOffset.UTC),
                        branch.createdBy
                    )
                )
                .coAwait()

            val row = result.first()
            return Branch(
                id = row.getUUID("id"),
                name = row.getString("name"),
                parentBranchId = row.getUUID("parent_branch_id"),
                createdAt = row.getOffsetDateTime("created_at").toInstant(),
                createdBy = row.getString("created_by")
            )
        } catch (e: Exception) {
            if (e.message?.contains("duplicate key value violates unique constraint") == true) {
                throw BranchAlreadyExistsException("Branch '${branch.name}' already exists")
            }
            throw e
        }
    }

    /**
     * Finds a branch by name.
     *
     * @param name The branch name to search for
     * @return The branch if found, null otherwise
     */
    suspend fun findByName(name: String): Branch? {
        val query = """
            SELECT id, name, parent_branch_id, created_at, created_by
            FROM branches
            WHERE name = $1
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(name))
            .coAwait()

        if (result.size() == 0) {
            return null
        }

        val row = result.first()
        return Branch(
            id = row.getUUID("id"),
            name = row.getString("name"),
            parentBranchId = row.getUUID("parent_branch_id"),
            createdAt = row.getOffsetDateTime("created_at").toInstant(),
            createdBy = row.getString("created_by")
        )
    }

    /**
     * Finds a branch by ID.
     *
     * @param id The branch ID to search for
     * @return The branch if found, null otherwise
     */
    suspend fun findById(id: UUID): Branch? {
        val query = """
            SELECT id, name, parent_branch_id, created_at, created_by
            FROM branches
            WHERE id = $1
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(id))
            .coAwait()

        if (result.size() == 0) {
            return null
        }

        val row = result.first()
        return Branch(
            id = row.getUUID("id"),
            name = row.getString("name"),
            parentBranchId = row.getUUID("parent_branch_id"),
            createdAt = row.getOffsetDateTime("created_at").toInstant(),
            createdBy = row.getString("created_by")
        )
    }

    /**
     * Lists all branches in the namespace.
     *
     * @return List of all branches, ordered by creation time descending
     */
    suspend fun list(): List<Branch> {
        val query = """
            SELECT id, name, parent_branch_id, created_at, created_by
            FROM branches
            ORDER BY created_at DESC
        """.trimIndent()

        val result = pool.query(query)
            .execute()
            .coAwait()

        return result.map { row ->
            Branch(
                id = row.getUUID("id"),
                name = row.getString("name"),
                parentBranchId = row.getUUID("parent_branch_id"),
                createdAt = row.getOffsetDateTime("created_at").toInstant(),
                createdBy = row.getString("created_by")
            )
        }
    }

    /**
     * Deletes a branch by name.
     *
     * Note: This will fail if there are data entries referencing this branch
     * due to foreign key constraints.
     *
     * @param name The branch name to delete
     * @return true if the branch was deleted, false if it didn't exist
     * @throws BranchInUseException if the branch cannot be deleted due to foreign key constraints
     */
    suspend fun delete(name: String): Boolean {
        val query = """
            DELETE FROM branches
            WHERE name = $1
        """.trimIndent()

        try {
            val result = pool.preparedQuery(query)
                .execute(Tuple.of(name))
                .coAwait()

            return result.rowCount() > 0
        } catch (e: Exception) {
            if (e.message?.contains("violates foreign key constraint") == true) {
                throw BranchInUseException("Branch '$name' cannot be deleted because it has data entries")
            }
            throw e
        }
    }
}

/**
 * Exception thrown when attempting to create a branch that already exists.
 */
class BranchAlreadyExistsException(message: String) : Exception(message)

/**
 * Exception thrown when attempting to delete a branch that is still in use.
 */
class BranchInUseException(message: String) : Exception(message)
