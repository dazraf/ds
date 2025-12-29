package com.example.service.repositories

import com.example.service.database.DatabaseManager
import com.example.service.database.LiquibaseRunner
import com.example.service.models.Namespace
import com.example.service.models.NamespaceStatus
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for managing namespaces in the registry database.
 *
 * Responsibilities:
 * - CRUD operations on the namespaces table
 * - Database creation via DatabaseManager
 * - Running migrations on new namespace databases
 */
class NamespaceRepository(
    private val registryPool: Pool,
    private val databaseManager: DatabaseManager
) {
    /**
     * Creates a new namespace.
     *
     * This operation:
     * 1. Inserts namespace metadata into registry database
     * 2. Creates a new PostgreSQL database for the namespace
     * 3. Runs Liquibase migrations on the new database
     *
     * @param namespace The namespace to create
     * @return The created namespace
     * @throws NamespaceAlreadyExistsException if namespace with same name exists
     */
    suspend fun create(namespace: Namespace): Namespace {
        logger.info { "Creating namespace: ${namespace.name}" }

        try {
            // 1. Insert into registry database
            val query = """
                INSERT INTO namespaces (id, name, database_name, created_at, created_by, status)
                VALUES ($1, $2, $3, $4, $5, $6)
                RETURNING id, name, database_name, created_at, created_by, status
            """.trimIndent()

            val result = registryPool.preparedQuery(query)
                .execute(
                    Tuple.of(
                        namespace.id,
                        namespace.name,
                        namespace.databaseName,
                        java.time.OffsetDateTime.ofInstant(namespace.createdAt, java.time.ZoneOffset.UTC),
                        namespace.createdBy,
                        namespace.status.name.lowercase()
                    )
                )
                .coAwait()

            val row = result.first()
            val createdNamespace = Namespace(
                id = row.getUUID("id"),
                name = row.getString("name"),
                databaseName = row.getString("database_name"),
                createdAt = row.getLocalDateTime("created_at").toInstant(java.time.ZoneOffset.UTC),
                createdBy = row.getString("created_by"),
                status = NamespaceStatus.valueOf(row.getString("status").uppercase())
            )

            logger.info { "Namespace metadata created in registry: ${namespace.name}" }

            // 2. Create the actual database
            databaseManager.createNamespaceDatabase(namespace.name)

            // 3. Run migrations on the new database
            val config = databaseManager.createNamespaceConfig(namespace.name)
            val runner = LiquibaseRunner.from(config)
            runner.runMigrations("db/changelog/namespace/db.changelog-master.yaml")

            logger.info { "Namespace created successfully: ${namespace.name} (database: ${namespace.databaseName})" }

            return createdNamespace
        } catch (e: io.vertx.pgclient.PgException) {
            if (e.message?.contains("duplicate key") == true) {
                throw NamespaceAlreadyExistsException("Namespace '${namespace.name}' already exists")
            }
            throw e
        }
    }

    /**
     * Finds a namespace by name.
     *
     * @param name The namespace name
     * @return The namespace if found, null otherwise
     */
    suspend fun findByName(name: String): Namespace? {
        val query = """
            SELECT id, name, database_name, created_at, created_by, status
            FROM namespaces
            WHERE name = $1
        """.trimIndent()

        val result = registryPool.preparedQuery(query)
            .execute(Tuple.of(name))
            .coAwait()

        if (result.size() == 0) {
            return null
        }

        val row = result.first()
        return Namespace(
            id = row.getUUID("id"),
            name = row.getString("name"),
            databaseName = row.getString("database_name"),
            createdAt = row.getLocalDateTime("created_at").toInstant(java.time.ZoneOffset.UTC),
            createdBy = row.getString("created_by"),
            status = NamespaceStatus.valueOf(row.getString("status").uppercase())
        )
    }

    /**
     * Lists all namespaces.
     *
     * @param includeDeleted Whether to include deleted namespaces
     * @return List of namespaces
     */
    suspend fun list(includeDeleted: Boolean = false): List<Namespace> {
        val query = if (includeDeleted) {
            "SELECT id, name, database_name, created_at, created_by, status FROM namespaces ORDER BY created_at DESC"
        } else {
            "SELECT id, name, database_name, created_at, created_by, status FROM namespaces WHERE status != 'deleted' ORDER BY created_at DESC"
        }

        val result = registryPool.query(query).execute().coAwait()

        return result.map { row ->
            Namespace(
                id = row.getUUID("id"),
                name = row.getString("name"),
                databaseName = row.getString("database_name"),
                createdAt = row.getLocalDateTime("created_at").toInstant(java.time.ZoneOffset.UTC),
                createdBy = row.getString("created_by"),
                status = NamespaceStatus.valueOf(row.getString("status").uppercase())
            )
        }
    }

    /**
     * Deletes a namespace (soft delete - marks as deleted).
     *
     * @param name The namespace name
     * @return true if deleted, false if not found
     */
    suspend fun delete(name: String): Boolean {
        val query = """
            UPDATE namespaces
            SET status = 'deleted'
            WHERE name = $1 AND status != 'deleted'
        """.trimIndent()

        val result = registryPool.preparedQuery(query)
            .execute(Tuple.of(name))
            .coAwait()

        val deleted = result.rowCount() > 0

        if (deleted) {
            logger.info { "Namespace marked as deleted: $name" }
        }

        return deleted
    }

    /**
     * Permanently deletes a namespace (hard delete).
     *
     * WARNING: This drops the database and removes all data!
     *
     * @param name The namespace name
     */
    suspend fun permanentlyDelete(name: String) {
        findByName(name) ?: throw NamespaceNotFoundException("Namespace '$name' not found")

        // Drop the database
        databaseManager.dropNamespaceDatabase(name)

        // Delete from registry
        val query = "DELETE FROM namespaces WHERE name = $1"
        registryPool.preparedQuery(query).execute(Tuple.of(name)).coAwait()

        logger.warn { "Namespace permanently deleted: $name (database dropped)" }
    }
}

class NamespaceAlreadyExistsException(message: String) : Exception(message)
class NamespaceNotFoundException(message: String) : Exception(message)
