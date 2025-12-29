package com.example.service.database

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import org.testcontainers.containers.PostgreSQLContainer

class DatabaseManagerTest : FreeSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("postgres")
        withPassword("postgres")
    }

    val postgresListener = postgres.perSpec()
    listener(postgresListener)

    lateinit var vertx: Vertx
    lateinit var dbManager: DatabaseManager

    beforeSpec {
        vertx = Vertx.vertx()

        // Admin config must connect to 'postgres' database to create/drop databases
        val adminConfig = DatabaseConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = "postgres",  // Changed from "testdb" to "postgres"
            user = "postgres",
            password = "postgres",
            maxPoolSize = 2
        )

        // Registry config uses testdb as the registry database
        val registryConfig = DatabaseConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = "testdb",
            user = "postgres",
            password = "postgres",
            maxPoolSize = 10
        )

        dbManager = DatabaseManager(adminConfig, registryConfig, vertx)
    }

    afterSpec {
        dbManager.close()
        vertx.close().coAwait()
    }

    "DatabaseManager" - {
        "should create a namespace database" {
            val dbName = dbManager.createNamespaceDatabase("test-namespace")

            // Database name has hyphens replaced with underscores for PostgreSQL compatibility
            dbName shouldBe "ds_ns_test_namespace"

            // Verify database exists
            val exists = dbManager.databaseExists("test-namespace")
            exists shouldBe true
        }

        "should list namespace databases" {
            dbManager.createNamespaceDatabase("list-test-1")
            dbManager.createNamespaceDatabase("list-test-2")

            val databases = dbManager.listNamespaceDatabases()

            // Database names have hyphens replaced with underscores
            databases shouldContain "ds_ns_list_test_1"
            databases shouldContain "ds_ns_list_test_2"
        }

        "should get a connection pool for namespace database" {
            dbManager.createNamespaceDatabase("connection-test")

            val pool = dbManager.getNamespaceConnection("connection-test")

            // Test connection works
            val result = pool.query("SELECT 1 as test").execute().coAwait()
            result.first().getInteger("test") shouldBe 1
        }

        "should reuse cached connection pool" {
            dbManager.createNamespaceDatabase("cache-test")

            val pool1 = dbManager.getNamespaceConnection("cache-test")
            val pool2 = dbManager.getNamespaceConnection("cache-test")

            pool1 shouldBe pool2
        }

        "should drop a namespace database" {
            dbManager.createNamespaceDatabase("drop-test")

            val existsBefore = dbManager.databaseExists("drop-test")
            existsBefore shouldBe true

            dbManager.dropNamespaceDatabase("drop-test")

            val existsAfter = dbManager.databaseExists("drop-test")
            existsAfter shouldBe false
        }

        "should validate namespace name format" {
            // Valid names
            dbManager.createNamespaceDatabase("valid-name")
            dbManager.createNamespaceDatabase("name123")
            dbManager.createNamespaceDatabase("test-namespace-456")

            // Invalid names
            shouldThrow<IllegalArgumentException> {
                dbManager.createNamespaceDatabase("Invalid_Name")
            }

            shouldThrow<IllegalArgumentException> {
                dbManager.createNamespaceDatabase("invalid name")
            }

            shouldThrow<IllegalArgumentException> {
                dbManager.createNamespaceDatabase("UPPERCASE")
            }

            shouldThrow<IllegalArgumentException> {
                dbManager.createNamespaceDatabase("")
            }
        }

        "should run Liquibase migrations on namespace database" {
            val dbName = dbManager.createNamespaceDatabase("migration-test")

            val config = DatabaseConfig(
                host = postgres.host,
                port = postgres.firstMappedPort,
                database = dbName,  // Use the returned database name (with underscores)
                user = "postgres",
                password = "postgres"
            )

            val runner = LiquibaseRunner.from(config)
            runner.runMigrations("db/changelog/namespace/db.changelog-master.yaml")

            // Verify tables were created
            val pool = dbManager.getNamespaceConnection("migration-test")
            val result = pool.query(
                """
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                ORDER BY tablename
                """.trimIndent()
            ).execute().coAwait()

            val tables = result.map { it.getString("tablename") }.toSet()

            tables shouldContain "branches"
            tables shouldContain "data_entries"
            tables shouldContain "tags"
        }

        "should verify default main branch is created after migrations" {
            val dbName = dbManager.createNamespaceDatabase("main-branch-test")

            val config = DatabaseConfig(
                host = postgres.host,
                port = postgres.firstMappedPort,
                database = dbName,  // Use the returned database name (with underscores)
                user = "postgres",
                password = "postgres"
            )

            val runner = LiquibaseRunner.from(config)
            runner.runMigrations("db/changelog/namespace/db.changelog-master.yaml")

            // Verify main branch exists
            val pool = dbManager.getNamespaceConnection("main-branch-test")
            val result = pool.query(
                "SELECT name, created_by FROM branches WHERE name = 'main'"
            ).execute().coAwait()

            result.size() shouldBe 1
            result.first().getString("name") shouldBe "main"
            result.first().getString("created_by") shouldBe "system"
        }
    }
})
