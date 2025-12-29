package com.example.service.database

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages dynamic database creation and connection pooling for namespace databases.
 *
 * Responsibilities:
 * - Create/drop PostgreSQL databases dynamically
 * - Maintain connection pools per namespace (cached)
 * - Provide access to registry database
 * - Clean up resources
 */
class DatabaseManager(
    private val adminConfig: DatabaseConfig,
    private val registryConfig: DatabaseConfig,
    private val vertx: Vertx
) {
    // Admin pool for CREATE/DROP DATABASE operations
    private val adminPool: Pool = Pool.pool(
        vertx,
        adminConfig.toPgConnectOptions(),
        adminConfig.toPoolOptions()
    )

    // Registry pool for namespace metadata
    private val registryPool: Pool = Pool.pool(
        vertx,
        registryConfig.toPgConnectOptions(),
        registryConfig.toPoolOptions()
    )
    private val namespacePools: MutableMap<String, Pool> = ConcurrentHashMap()

    init {
        logger.info { "DatabaseManager initialized with admin and registry pools" }
    }

    /**
     * Creates a new PostgreSQL database for a namespace.
     *
     * @param namespaceName The namespace name (will be prefixed with ds_ns_)
     * @return The created database name
     * @throws IllegalArgumentException if namespace name is invalid
     */
    suspend fun createNamespaceDatabase(namespaceName: String): String {
        validateNamespaceName(namespaceName)

        val dbName = toDatabaseName(namespaceName)

        logger.info { "Creating database: $dbName" }

        try {
            // Note: CREATE DATABASE cannot be parameterized, so we validate carefully
            adminPool.query("CREATE DATABASE $dbName").execute().coAwait()
            logger.info { "Database created successfully: $dbName" }
            return dbName
        } catch (e: Exception) {
            logger.error(e) { "Failed to create database: $dbName" }
            throw DatabaseCreationException("Failed to create database $dbName", e)
        }
    }

    /**
     * Drops a PostgreSQL database for a namespace.
     *
     * @param namespaceName The namespace name
     */
    suspend fun dropNamespaceDatabase(namespaceName: String) {
        val dbName = toDatabaseName(namespaceName)

        logger.info { "Dropping database: $dbName" }

        // Close and remove pool if exists
        namespacePools.remove(dbName)?.let { pool ->
            pool.close().coAwait()
            logger.info { "Closed connection pool for $dbName" }
        }

        try {
            // Terminate active connections first
            adminPool.query(
                """
                SELECT pg_terminate_backend(pid)
                FROM pg_stat_activity
                WHERE datname = '$dbName' AND pid <> pg_backend_pid()
                """.trimIndent()
            ).execute().coAwait()

            // Drop database
            adminPool.query("DROP DATABASE IF EXISTS $dbName").execute().coAwait()
            logger.info { "Database dropped successfully: $dbName" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to drop database: $dbName" }
            throw DatabaseDeletionException("Failed to drop database $dbName", e)
        }
    }

    /**
     * Checks if a namespace database exists.
     *
     * @param namespaceName The namespace name
     * @return true if database exists, false otherwise
     */
    suspend fun databaseExists(namespaceName: String): Boolean {
        val dbName = toDatabaseName(namespaceName)

        val result = adminPool.preparedQuery(
            "SELECT 1 FROM pg_database WHERE datname = $1"
        ).execute(io.vertx.sqlclient.Tuple.of(dbName)).coAwait()

        return result.size() > 0
    }

    /**
     * Gets a connection pool for a specific namespace database.
     * Pools are cached and reused across requests.
     *
     * @param namespaceName The namespace name
     * @return Connection pool for the namespace database
     */
    fun getNamespaceConnection(namespaceName: String): Pool {
        val dbName = toDatabaseName(namespaceName)

        return namespacePools.getOrPut(dbName) {
            logger.info { "Creating connection pool for namespace: $namespaceName (database: $dbName)" }

            val config = registryConfig.copy(database = dbName)
            Pool.pool(
                vertx,
                config.toPgConnectOptions(),
                config.toPoolOptions()
            )
        }
    }

    /**
     * Gets the registry database connection pool.
     *
     * @return Connection pool for the registry database
     */
    fun getRegistryConnection(): Pool {
        return registryPool
    }

    /**
     * Creates a DatabaseConfig for a namespace database using admin connection details.
     * Used for running migrations and other operations that need JDBC connections.
     *
     * @param namespaceName The namespace name
     * @return DatabaseConfig for the namespace database
     */
    fun createNamespaceConfig(namespaceName: String): DatabaseConfig {
        val dbName = toDatabaseName(namespaceName)
        return adminConfig.copy(database = dbName)
    }

    /**
     * Lists all namespace databases (those starting with ds_ns_).
     *
     * @return List of database names
     */
    suspend fun listNamespaceDatabases(): List<String> {
        val result = adminPool.query(
            "SELECT datname FROM pg_database WHERE datname LIKE 'ds_ns_%'"
        ).execute().coAwait()

        return result.map { it.getString("datname") }
    }

    /**
     * Closes all connection pools and releases resources.
     */
    suspend fun close() {
        logger.info { "Closing all database connections" }

        // Close namespace pools
        namespacePools.values.forEach { pool ->
            pool.close().coAwait()
        }
        namespacePools.clear()

        // Close admin and registry pools
        adminPool.close().coAwait()
        registryPool.close().coAwait()

        logger.info { "All database connections closed" }
    }

    private fun toDatabaseName(namespaceName: String): String {
        // Replace hyphens with underscores for PostgreSQL database name compatibility
        val sanitizedName = namespaceName.replace("-", "_")
        return "ds_ns_$sanitizedName"
    }

    private fun validateNamespaceName(name: String) {
        require(name.matches(Regex("^[a-z0-9-]+$"))) {
            "Namespace name must contain only lowercase letters, numbers, and hyphens"
        }
        require(name.length in 1..63) {
            "Namespace name must be between 1 and 63 characters"
        }
    }
}

class DatabaseCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DatabaseDeletionException(message: String, cause: Throwable? = null) : Exception(message, cause)
