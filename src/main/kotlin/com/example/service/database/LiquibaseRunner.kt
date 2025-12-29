package com.example.service.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import mu.KotlinLogging
import java.sql.DriverManager

private val logger = KotlinLogging.logger {}

/**
 * Wrapper for Liquibase to run database migrations programmatically.
 *
 * Runs migrations in IO dispatcher to avoid blocking Vert.x event loop.
 */
class LiquibaseRunner(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String
) {
    /**
     * Runs Liquibase migrations for the specified changelog file.
     *
     * @param changelogPath Path to changelog file (relative to resources), e.g., "db/changelog/registry/db.changelog-master.yaml"
     * @param contexts Optional Liquibase contexts (e.g., "dev", "prod")
     */
    suspend fun runMigrations(changelogPath: String, contexts: String? = null) = withContext(Dispatchers.IO) {
        logger.info { "Running Liquibase migrations: $changelogPath on $jdbcUrl" }

        try {
            // Ensure PostgreSQL driver is loaded
            Class.forName("org.postgresql.Driver")

            val connection = DriverManager.getConnection(jdbcUrl, username, password)
            connection.use {
                val database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))

                val liquibase = Liquibase(
                    changelogPath,
                    ClassLoaderResourceAccessor(),
                    database
                )

                val contextsObj = if (contexts != null) Contexts(contexts) else Contexts()

                liquibase.update(contextsObj)

                logger.info { "Migrations completed successfully for: $jdbcUrl" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to run migrations on $jdbcUrl" }
            throw MigrationException("Failed to run migrations: ${e.message}", e)
        }
    }

    /**
     * Rolls back the last N change sets.
     *
     * @param count Number of change sets to roll back
     */
    suspend fun rollback(count: Int) = withContext(Dispatchers.IO) {
        logger.info { "Rolling back $count changesets on $jdbcUrl" }

        try {
            Class.forName("org.postgresql.Driver")

            val connection = DriverManager.getConnection(jdbcUrl, username, password)
            connection.use {
                val database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))

                val liquibase = Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    ClassLoaderResourceAccessor(),
                    database
                )

                liquibase.rollback(count, "")

                logger.info { "Rollback completed successfully for: $jdbcUrl" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to rollback migrations on $jdbcUrl" }
            throw MigrationException("Failed to rollback: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Creates a LiquibaseRunner from a DatabaseConfig.
         */
        fun from(config: DatabaseConfig): LiquibaseRunner {
            return LiquibaseRunner(
                jdbcUrl = config.toJdbcUrl(),
                username = config.user,
                password = config.password
            )
        }
    }
}

class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)
