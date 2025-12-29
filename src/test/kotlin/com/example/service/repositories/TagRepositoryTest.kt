package com.example.service.repositories

import com.example.service.database.DatabaseConfig
import com.example.service.database.LiquibaseRunner
import com.example.service.models.DataEntry
import com.example.service.models.Tag
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant

/**
 * Comprehensive tests for TagRepository.
 *
 * Tests cover all CRUD operations, tag searching,
 * and edge cases.
 */
class TagRepositoryTest : FreeSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("postgres")
        withPassword("postgres")
    }

    val postgresListener = postgres.perSpec()
    listener(postgresListener)

    lateinit var vertx: Vertx
    lateinit var pool: Pool
    lateinit var branchRepository: BranchRepository
    lateinit var dataEntryRepository: DataEntryRepository
    lateinit var repository: TagRepository
    lateinit var mainBranchId: java.util.UUID

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

        // Run migrations
        val migrationRunner = LiquibaseRunner.from(config)
        migrationRunner.runMigrations("db/changelog/namespace/db.changelog-master.yaml")

        branchRepository = BranchRepository(pool)
        dataEntryRepository = DataEntryRepository(pool)
        repository = TagRepository(pool)

        // Get main branch ID
        val mainBranch = branchRepository.findByName("main")
        mainBranchId = mainBranch!!.id
    }

    afterSpec {
        pool.close().coAwait()
        vertx.close().coAwait()
    }

    "TagRepository.create" - {
        "should create a tag for a data entry" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "tag-test-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Test data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val tag = Tag.create(entry.id, "important")

            val created = repository.create(tag)

            created.id shouldBe tag.id
            created.dataEntryId shouldBe entry.id
            created.value shouldBe "important"
        }

        "should throw TagAlreadyExistsException for duplicate tags" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "duplicate-tag-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Test data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val tag1 = Tag.create(entry.id, "duplicate")
            val tag2 = Tag.create(entry.id, "duplicate")

            repository.create(tag1)

            val exception = shouldThrow<TagAlreadyExistsException> {
                repository.create(tag2)
            }

            exception.message shouldBe "Tag 'duplicate' already exists for this data entry"
        }

        "should allow same tag value for different entries" {
            val entry1 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "multi-tag-doc-1",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 1".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val entry2 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "multi-tag-doc-2",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 2".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val tag1 = repository.create(Tag.create(entry1.id, "shared-tag"))
            val tag2 = repository.create(Tag.create(entry2.id, "shared-tag"))

            tag1.value shouldBe "shared-tag"
            tag2.value shouldBe "shared-tag"
            tag1.dataEntryId shouldNotBe tag2.dataEntryId
        }

        "should create multiple tags for same entry" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "multi-tag-entry",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "tag1"))
            repository.create(Tag.create(entry.id, "tag2"))
            repository.create(Tag.create(entry.id, "tag3"))

            val tags = repository.findByDataEntryId(entry.id)

            tags shouldHaveSize 3
            tags.map { it.value } shouldContain "tag1"
            tags.map { it.value } shouldContain "tag2"
            tags.map { it.value } shouldContain "tag3"
        }
    }

    "TagRepository.findByDataEntryId" - {
        "should find all tags for a data entry" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "find-tags-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "alpha"))
            repository.create(Tag.create(entry.id, "beta"))
            repository.create(Tag.create(entry.id, "gamma"))

            val tags = repository.findByDataEntryId(entry.id)

            tags shouldHaveSize 3
            // Should be ordered alphabetically by value
            tags[0].value shouldBe "alpha"
            tags[1].value shouldBe "beta"
            tags[2].value shouldBe "gamma"
        }

        "should return empty list for entry with no tags" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "no-tags-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val tags = repository.findByDataEntryId(entry.id)

            tags shouldHaveSize 0
        }

        "should return empty list for non-existent entry" {
            val tags = repository.findByDataEntryId(java.util.UUID.randomUUID())

            tags shouldHaveSize 0
        }
    }

    "TagRepository.findDataEntriesByTag" - {
        "should find all data entries with a specific tag" {
            val entry1 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "search-tag-doc-1",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 1".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val entry2 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "search-tag-doc-2",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 2".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val entry3 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "search-tag-doc-3",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 3".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry1.id, "findme"))
            repository.create(Tag.create(entry2.id, "findme"))
            repository.create(Tag.create(entry3.id, "other"))

            val foundIds = repository.findDataEntriesByTag("findme")

            foundIds shouldHaveSize 2
            foundIds shouldContain entry1.id
            foundIds shouldContain entry2.id
        }

        "should return empty list for tag with no entries" {
            val foundIds = repository.findDataEntriesByTag("non-existent-tag")

            foundIds shouldHaveSize 0
        }
    }

    "TagRepository.delete" - {
        "should delete a specific tag from an entry" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "delete-tag-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "delete-me"))
            repository.create(Tag.create(entry.id, "keep-me"))

            val deleted = repository.delete(entry.id, "delete-me")

            deleted shouldBe true

            val tags = repository.findByDataEntryId(entry.id)
            tags shouldHaveSize 1
            tags[0].value shouldBe "keep-me"
        }

        "should return false for non-existent tag" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "no-delete-tag-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val deleted = repository.delete(entry.id, "non-existent")

            deleted shouldBe false
        }

        "should return false for already deleted tag" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "already-deleted-tag-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "remove"))
            repository.delete(entry.id, "remove")

            val deletedAgain = repository.delete(entry.id, "remove")

            deletedAgain shouldBe false
        }
    }

    "TagRepository.deleteAllForDataEntry" - {
        "should delete all tags for an entry" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "delete-all-tags-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "tag1"))
            repository.create(Tag.create(entry.id, "tag2"))
            repository.create(Tag.create(entry.id, "tag3"))

            val deletedCount = repository.deleteAllForDataEntry(entry.id)

            deletedCount shouldBe 3

            val tags = repository.findByDataEntryId(entry.id)
            tags shouldHaveSize 0
        }

        "should return 0 for entry with no tags" {
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "no-tags-delete-all-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val deletedCount = repository.deleteAllForDataEntry(entry.id)

            deletedCount shouldBe 0
        }

        "should return 0 for non-existent entry" {
            val deletedCount = repository.deleteAllForDataEntry(java.util.UUID.randomUUID())

            deletedCount shouldBe 0
        }
    }

    "TagRepository - Cascade Behavior" - {
        "should cascade delete tags when data entry is deleted" {
            // Create data entry with tags
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "cascade-delete-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "tag1"))
            repository.create(Tag.create(entry.id, "tag2"))
            repository.create(Tag.create(entry.id, "tag3"))

            // Verify tags exist
            val tagsBefore = repository.findByDataEntryId(entry.id)
            tagsBefore shouldHaveSize 3

            // Hard delete the data entry using SQL
            val deleteQuery = """
                DELETE FROM data_entries WHERE id = $1
            """.trimIndent()

            pool.preparedQuery(deleteQuery)
                .execute(io.vertx.sqlclient.Tuple.of(entry.id))
                .coAwait()

            // Tags should be cascade deleted
            val tagsAfter = repository.findByDataEntryId(entry.id)
            tagsAfter shouldHaveSize 0
        }

        "should not affect tags when data entry is soft deleted" {
            // Create data entry with tags
            val entry = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "soft-delete-doc",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry.id, "tag1"))
            repository.create(Tag.create(entry.id, "tag2"))

            // Soft delete (sets transaction_to)
            dataEntryRepository.delete(mainBranchId, "documents", "soft-delete-doc")

            // Tags should still exist (soft delete doesn't cascade)
            val tags = repository.findByDataEntryId(entry.id)
            tags shouldHaveSize 2
        }

        "should handle tags on multiple data entries independently" {
            val entry1 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "cascade-multi-doc-1",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 1".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val entry2 = dataEntryRepository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "cascade-multi-doc-2",
                    validFrom = Instant.now(),
                    validTo = null,
                    data = "Data 2".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            repository.create(Tag.create(entry1.id, "shared-tag"))
            repository.create(Tag.create(entry2.id, "shared-tag"))

            // Hard delete entry1
            val deleteQuery = """
                DELETE FROM data_entries WHERE id = $1
            """.trimIndent()

            pool.preparedQuery(deleteQuery)
                .execute(io.vertx.sqlclient.Tuple.of(entry1.id))
                .coAwait()

            // entry1's tags should be gone
            val tags1 = repository.findByDataEntryId(entry1.id)
            tags1 shouldHaveSize 0

            // entry2's tags should remain
            val tags2 = repository.findByDataEntryId(entry2.id)
            tags2 shouldHaveSize 1
            tags2[0].value shouldBe "shared-tag"
        }
    }
})
