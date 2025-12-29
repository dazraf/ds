package com.example.service.repositories

import com.example.service.database.DatabaseConfig
import com.example.service.database.LiquibaseRunner
import com.example.service.models.Branch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Comprehensive tests for BranchRepository.
 *
 * Tests cover all CRUD operations, parent branch relationships,
 * error handling, and edge cases.
 */
class BranchRepositoryTest : FreeSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("postgres")
        withPassword("postgres")
    }

    val postgresListener = postgres.perSpec()
    listener(postgresListener)

    lateinit var vertx: Vertx
    lateinit var pool: Pool
    lateinit var repository: BranchRepository

    beforeSpec {
        vertx = Vertx.vertx()

        val config = DatabaseConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = "testdb",
            user = "postgres",
            password = "postgres",
            maxPoolSize = 10
        )

        // Create pool
        pool = Pool.pool(
            vertx,
            config.toPgConnectOptions(),
            config.toPoolOptions()
        )

        // Run migrations to create branches table
        val migrationRunner = LiquibaseRunner.from(config)
        migrationRunner.runMigrations("db/changelog/namespace/db.changelog-master.yaml")

        repository = BranchRepository(pool)
    }

    afterSpec {
        pool.close().coAwait()
        vertx.close().coAwait()
    }

    "BranchRepository.create" - {
        "should create a branch with all metadata" {
            val branch = Branch.create("feature/test", "test-user")

            val created = repository.create(branch)

            created.id shouldBe branch.id
            created.name shouldBe "feature/test"
            created.parentBranchId shouldBe null
            created.createdBy shouldBe "test-user"
            created.createdAt shouldNotBe null
        }

        "should create a branch with parent branch ID" {
            val parentBranch = repository.findByName("main")
            parentBranch shouldNotBe null

            val branch = Branch.create("feature/with-parent", "test-user", parentBranch!!.id)

            val created = repository.create(branch)

            created.parentBranchId shouldBe parentBranch.id
            created.name shouldBe "feature/with-parent"
        }

        "should throw BranchAlreadyExistsException for duplicate names" {
            val branch1 = Branch.create("duplicate-branch", "user1")
            val branch2 = Branch.create("duplicate-branch", "user2")

            repository.create(branch1)

            val exception = shouldThrow<BranchAlreadyExistsException> {
                repository.create(branch2)
            }

            exception.message shouldBe "Branch 'duplicate-branch' already exists"
        }

        "should allow branch names with slashes" {
            val branch = Branch.create("feature/xyz/123", "test-user")

            val created = repository.create(branch)

            created.name shouldBe "feature/xyz/123"
        }

        "should allow branch names with hyphens" {
            val branch = Branch.create("feature-branch-name", "test-user")

            val created = repository.create(branch)

            created.name shouldBe "feature-branch-name"
        }

        "should allow branch names with underscores" {
            val branch = Branch.create("feature_branch_name", "test-user")

            val created = repository.create(branch)

            created.name shouldBe "feature_branch_name"
        }

        "should allow mixed alphanumeric and special chars" {
            val branch = Branch.create("dev/feature-123_test", "test-user")

            val created = repository.create(branch)

            created.name shouldBe "dev/feature-123_test"
        }
    }

    "BranchRepository.findByName" - {
        "should find an existing branch by name" {
            val branch = Branch.create("find-test", "test-user")
            repository.create(branch)

            val found = repository.findByName("find-test")

            found shouldNotBe null
            found?.name shouldBe "find-test"
            found?.createdBy shouldBe "test-user"
        }

        "should return null for non-existent branch" {
            val found = repository.findByName("non-existent-branch")

            found shouldBe null
        }

        "should find the default main branch" {
            val found = repository.findByName("main")

            found shouldNotBe null
            found?.name shouldBe "main"
            found?.createdBy shouldBe "system"
        }

        "should find branch with slashes in name" {
            val branch = Branch.create("feature/slash-test", "test-user")
            repository.create(branch)

            val found = repository.findByName("feature/slash-test")

            found shouldNotBe null
            found?.name shouldBe "feature/slash-test"
        }
    }

    "BranchRepository.findById" - {
        "should find an existing branch by ID" {
            val branch = Branch.create("findbyid-test", "test-user")
            val created = repository.create(branch)

            val found = repository.findById(created.id)

            found shouldNotBe null
            found?.id shouldBe created.id
            found?.name shouldBe "findbyid-test"
        }

        "should return null for non-existent ID" {
            val found = repository.findById(java.util.UUID.randomUUID())

            found shouldBe null
        }

        "should find main branch by ID" {
            val main = repository.findByName("main")
            main shouldNotBe null

            val found = repository.findById(main!!.id)

            found shouldNotBe null
            found?.id shouldBe main.id
            found?.name shouldBe "main"
        }
    }

    "BranchRepository.list" - {
        "should list all branches" {
            repository.create(Branch.create("list-test-1", "user1"))
            repository.create(Branch.create("list-test-2", "user2"))
            repository.create(Branch.create("list-test-3", "user3"))

            val branches = repository.list()

            val names = branches.map { it.name }
            names shouldContain "main"
            names shouldContain "list-test-1"
            names shouldContain "list-test-2"
            names shouldContain "list-test-3"
        }

        "should return branches ordered by creation time descending" {
            repository.create(Branch.create("order-test-1", "user1"))
            Thread.sleep(10) // Ensure different timestamps
            repository.create(Branch.create("order-test-2", "user2"))
            Thread.sleep(10)
            repository.create(Branch.create("order-test-3", "user3"))

            val branches = repository.list()

            val orderTestBranches = branches.filter { it.name.startsWith("order-test-") }
            orderTestBranches.size shouldBe 3
            orderTestBranches[0].name shouldBe "order-test-3"
            orderTestBranches[1].name shouldBe "order-test-2"
            orderTestBranches[2].name shouldBe "order-test-1"
        }

        "should include branches with parent relationships" {
            val parent = repository.findByName("main")
            val child = Branch.create("list-parent-test", "test-user", parent!!.id)
            repository.create(child)

            val branches = repository.list()

            val found = branches.find { it.name == "list-parent-test" }
            found shouldNotBe null
            found?.parentBranchId shouldBe parent.id
        }
    }

    "BranchRepository.delete" - {
        "should delete a branch" {
            val branch = Branch.create("delete-test", "test-user")
            repository.create(branch)

            val deleted = repository.delete("delete-test")

            deleted shouldBe true

            val found = repository.findByName("delete-test")
            found shouldBe null
        }

        "should return false for non-existent branch" {
            val deleted = repository.delete("non-existent-branch")

            deleted shouldBe false
        }

        "should return false for already deleted branch" {
            val branch = Branch.create("already-deleted", "test-user")
            repository.create(branch)
            repository.delete("already-deleted")

            val deletedAgain = repository.delete("already-deleted")

            deletedAgain shouldBe false
        }

        "should throw BranchInUseException if branch has data entries" {
            val branch = Branch.create("branch-with-data", "test-user")
            repository.create(branch)

            // Insert a data entry referencing this branch
            val query = """
                INSERT INTO data_entries (
                    id, branch_id, data_type, name,
                    valid_from, valid_to, transaction_from, transaction_to,
                    data, media_type, size_bytes, created_by
                )
                VALUES (
                    gen_random_uuid(), (SELECT id FROM branches WHERE name = 'branch-with-data'),
                    'test-type', 'test-name',
                    NOW(), 'infinity'::TIMESTAMPTZ, NOW(), 'infinity'::TIMESTAMPTZ,
                    E'\\x0123456789'::BYTEA, 'application/octet-stream', 5, 'test-user'
                )
            """.trimIndent()

            pool.query(query).execute().coAwait()

            val exception = shouldThrow<BranchInUseException> {
                repository.delete("branch-with-data")
            }

            exception.message shouldBe "Branch 'branch-with-data' cannot be deleted because it has data entries"
        }

        "should successfully delete branch with no data entries" {
            val branch = Branch.create("empty-branch", "test-user")
            repository.create(branch)

            // Verify it has no data entries
            val checkQuery = """
                SELECT COUNT(*) as count
                FROM data_entries
                WHERE branch_id = (SELECT id FROM branches WHERE name = 'empty-branch')
            """.trimIndent()

            val result = pool.query(checkQuery).execute().coAwait()
            val count = result.first().getLong("count")
            count shouldBe 0

            // Should delete successfully
            val deleted = repository.delete("empty-branch")
            deleted shouldBe true
        }

        "should prevent deletion of branch with multiple data entries" {
            val branch = Branch.create("branch-with-multiple-entries", "test-user")
            repository.create(branch)

            // Insert multiple data entries
            repeat(3) { i ->
                val query = """
                    INSERT INTO data_entries (
                        id, branch_id, data_type, name,
                        valid_from, valid_to, transaction_from, transaction_to,
                        data, media_type, size_bytes, created_by
                    )
                    VALUES (
                        gen_random_uuid(),
                        (SELECT id FROM branches WHERE name = 'branch-with-multiple-entries'),
                        'test-type', 'test-name-$i',
                        NOW(), 'infinity'::TIMESTAMPTZ, NOW(), 'infinity'::TIMESTAMPTZ,
                        E'\\x0123456789'::BYTEA, 'application/octet-stream', 5, 'test-user'
                    )
                """.trimIndent()

                pool.query(query).execute().coAwait()
            }

            val exception = shouldThrow<BranchInUseException> {
                repository.delete("branch-with-multiple-entries")
            }

            exception.message shouldBe "Branch 'branch-with-multiple-entries' cannot be deleted because it has data entries"
        }
    }
})
