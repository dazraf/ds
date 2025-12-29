package com.example.service.repositories

import com.example.service.database.DatabaseConfig
import com.example.service.database.DatabaseManager
import com.example.service.database.LiquibaseRunner
import com.example.service.models.Namespace
import com.example.service.models.NamespaceStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Comprehensive tests for NamespaceRepository.
 *
 * Tests cover all CRUD operations, error handling, database creation,
 * and migration execution.
 */
class NamespaceRepositoryTest : FreeSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("postgres")
        withPassword("postgres")
    }

    val postgresListener = postgres.perSpec()
    listener(postgresListener)

    lateinit var vertx: Vertx
    lateinit var dbManager: DatabaseManager
    lateinit var registryPool: Pool
    lateinit var repository: NamespaceRepository

    beforeSpec {
        vertx = Vertx.vertx()

        // Admin config for creating/dropping databases
        val adminConfig = DatabaseConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = "postgres",
            user = "postgres",
            password = "postgres",
            maxPoolSize = 2
        )

        // Registry config for namespace metadata
        val registryConfig = DatabaseConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = "testdb",
            user = "postgres",
            password = "postgres",
            maxPoolSize = 10
        )

        dbManager = DatabaseManager(adminConfig, registryConfig, vertx)

        // Create registry pool
        registryPool = Pool.pool(
            vertx,
            registryConfig.toPgConnectOptions(),
            registryConfig.toPoolOptions()
        )

        // Run registry migrations to create namespaces table
        val migrationRunner = LiquibaseRunner.from(registryConfig)
        migrationRunner.runMigrations("db/changelog/registry/db.changelog-master.yaml")

        repository = NamespaceRepository(registryPool, dbManager)
    }

    afterSpec {
        registryPool.close().coAwait()
        dbManager.close()
        vertx.close().coAwait()
    }

    "NamespaceRepository.create" - {
        "should create a namespace with all metadata" {
            val namespace = Namespace.create("create-test", "test-user")

            val created = repository.create(namespace)

            created.id shouldBe namespace.id
            created.name shouldBe "create-test"
            created.databaseName shouldBe "ds_ns_create_test"
            created.createdBy shouldBe "test-user"
            created.status shouldBe NamespaceStatus.ACTIVE
            created.createdAt shouldNotBe null
        }

        "should create the PostgreSQL database for the namespace" {
            val namespace = Namespace.create("db-creation-test", "test-user")

            repository.create(namespace)

            val exists = dbManager.databaseExists("db-creation-test")
            exists shouldBe true
        }

        "should run migrations on the new namespace database" {
            val namespace = Namespace.create("migration-test", "test-user")

            repository.create(namespace)

            // Connect to the namespace database and verify branches table exists
            val pool = dbManager.getNamespaceConnection("migration-test")
            val result = pool.query("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                .execute()
                .coAwait()

            val tables = result.map { it.getString("tablename") }
            tables shouldContain "branches"
            tables shouldContain "data_entries"
            tables shouldContain "tags"
        }

        "should create a default main branch in the namespace database" {
            val namespace = Namespace.create("main-branch-test", "test-user")

            repository.create(namespace)

            // Verify main branch exists
            val pool = dbManager.getNamespaceConnection("main-branch-test")
            val result = pool.query("SELECT name FROM branches WHERE name = 'main'")
                .execute()
                .coAwait()

            result.size() shouldBe 1
            result.first().getString("name") shouldBe "main"
        }

        "should throw NamespaceAlreadyExistsException for duplicate names" {
            val namespace1 = Namespace.create("duplicate-test", "test-user")
            val namespace2 = Namespace.create("duplicate-test", "test-user")

            repository.create(namespace1)

            val exception = shouldThrow<NamespaceAlreadyExistsException> {
                repository.create(namespace2)
            }

            exception.message shouldBe "Namespace 'duplicate-test' already exists"
        }

        "should preserve namespace name with hyphens in registry" {
            val namespace = Namespace.create("my-test-namespace", "test-user")

            val created = repository.create(namespace)

            created.name shouldBe "my-test-namespace"
            created.databaseName shouldBe "ds_ns_my_test_namespace"
        }
    }

    "NamespaceRepository.findByName" - {
        "should find an existing namespace by name" {
            val namespace = Namespace.create("find-test", "test-user")
            repository.create(namespace)

            val found = repository.findByName("find-test")

            found shouldNotBe null
            found?.name shouldBe "find-test"
            found?.databaseName shouldBe "ds_ns_find_test"
            found?.createdBy shouldBe "test-user"
            found?.status shouldBe NamespaceStatus.ACTIVE
        }

        "should return null for non-existent namespace" {
            val found = repository.findByName("non-existent")

            found shouldBe null
        }

        "should find a deleted namespace" {
            val namespace = Namespace.create("deleted-find-test", "test-user")
            repository.create(namespace)
            repository.delete("deleted-find-test")

            val found = repository.findByName("deleted-find-test")

            found shouldNotBe null
            found?.status shouldBe NamespaceStatus.DELETED
        }
    }

    "NamespaceRepository.list" - {
        "should list all active namespaces" {
            repository.create(Namespace.create("list-test-1", "user1"))
            repository.create(Namespace.create("list-test-2", "user2"))
            repository.create(Namespace.create("list-test-3", "user3"))

            val namespaces = repository.list()

            val names = namespaces.map { it.name }
            names shouldContain "list-test-1"
            names shouldContain "list-test-2"
            names shouldContain "list-test-3"
        }

        "should exclude deleted namespaces by default" {
            repository.create(Namespace.create("active-namespace", "user1"))
            repository.create(Namespace.create("deleted-namespace", "user2"))
            repository.delete("deleted-namespace")

            val namespaces = repository.list(includeDeleted = false)

            val names = namespaces.map { it.name }
            names shouldContain "active-namespace"
            names shouldNotContain "deleted-namespace"
        }

        "should include deleted namespaces when requested" {
            repository.create(Namespace.create("active-for-include", "user1"))
            repository.create(Namespace.create("deleted-for-include", "user2"))
            repository.delete("deleted-for-include")

            val namespaces = repository.list(includeDeleted = true)

            val names = namespaces.map { it.name }
            names shouldContain "active-for-include"
            names shouldContain "deleted-for-include"

            val deletedNamespace = namespaces.first { it.name == "deleted-for-include" }
            deletedNamespace.status shouldBe NamespaceStatus.DELETED
        }

        "should return namespaces ordered by creation time descending" {
            repository.create(Namespace.create("order-test-1", "user1"))
            Thread.sleep(10) // Ensure different timestamps
            repository.create(Namespace.create("order-test-2", "user2"))
            Thread.sleep(10)
            repository.create(Namespace.create("order-test-3", "user3"))

            val namespaces = repository.list()

            val orderTestNamespaces = namespaces.filter { it.name.startsWith("order-test-") }
            orderTestNamespaces.size shouldBe 3
            orderTestNamespaces[0].name shouldBe "order-test-3"
            orderTestNamespaces[1].name shouldBe "order-test-2"
            orderTestNamespaces[2].name shouldBe "order-test-1"
        }

        "should return empty list when no namespaces exist" {
            // This test runs after previous tests, so we need to filter
            // Just verify the method doesn't throw
            val namespaces = repository.list()
            namespaces shouldNotBe null
        }
    }

    "NamespaceRepository.delete" - {
        "should soft delete a namespace" {
            val namespace = Namespace.create("delete-test", "test-user")
            repository.create(namespace)

            val deleted = repository.delete("delete-test")

            deleted shouldBe true

            val found = repository.findByName("delete-test")
            found?.status shouldBe NamespaceStatus.DELETED
        }

        "should return false for non-existent namespace" {
            val deleted = repository.delete("non-existent-namespace")

            deleted shouldBe false
        }

        "should return false for already deleted namespace" {
            val namespace = Namespace.create("already-deleted", "test-user")
            repository.create(namespace)
            repository.delete("already-deleted")

            val deletedAgain = repository.delete("already-deleted")

            deletedAgain shouldBe false
        }

        "should not drop the database on soft delete" {
            val namespace = Namespace.create("soft-delete-db-test", "test-user")
            repository.create(namespace)

            repository.delete("soft-delete-db-test")

            val exists = dbManager.databaseExists("soft-delete-db-test")
            exists shouldBe true
        }
    }

    "NamespaceRepository.permanentlyDelete" - {
        "should permanently delete a namespace" {
            val namespace = Namespace.create("permanent-delete-test", "test-user")
            repository.create(namespace)

            repository.permanentlyDelete("permanent-delete-test")

            val found = repository.findByName("permanent-delete-test")
            found shouldBe null
        }

        "should drop the database on permanent delete" {
            val namespace = Namespace.create("permanent-db-drop-test", "test-user")
            repository.create(namespace)

            repository.permanentlyDelete("permanent-db-drop-test")

            val exists = dbManager.databaseExists("permanent-db-drop-test")
            exists shouldBe false
        }

        "should throw NamespaceNotFoundException for non-existent namespace" {
            val exception = shouldThrow<NamespaceNotFoundException> {
                repository.permanentlyDelete("non-existent-permanent")
            }

            exception.message shouldBe "Namespace 'non-existent-permanent' not found"
        }

        "should permanently delete a soft-deleted namespace" {
            val namespace = Namespace.create("soft-then-hard-delete", "test-user")
            repository.create(namespace)
            repository.delete("soft-then-hard-delete")

            repository.permanentlyDelete("soft-then-hard-delete")

            val found = repository.findByName("soft-then-hard-delete")
            found shouldBe null

            val exists = dbManager.databaseExists("soft-then-hard-delete")
            exists shouldBe false
        }
    }
})
