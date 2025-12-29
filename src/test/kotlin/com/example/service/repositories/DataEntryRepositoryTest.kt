package com.example.service.repositories

import com.example.service.database.DatabaseConfig
import com.example.service.database.LiquibaseRunner
import com.example.service.models.Branch
import com.example.service.models.DataEntry
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
import java.time.temporal.ChronoUnit

/**
 * Comprehensive tests for DataEntryRepository.
 *
 * Tests cover all CRUD operations, full bitemporal query capabilities,
 * automatic versioning, and edge cases.
 */
class DataEntryRepositoryTest : FreeSpec({
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
    lateinit var repository: DataEntryRepository
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
        repository = DataEntryRepository(pool)

        // Get main branch ID
        val mainBranch = branchRepository.findByName("main")
        mainBranchId = mainBranch!!.id
    }

    afterSpec {
        pool.close().coAwait()
        vertx.close().coAwait()
    }

    "DataEntryRepository.create" - {
        "should create a data entry with all metadata" {
            val data = "Hello, World!".toByteArray()
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "test-doc",
                validFrom = Instant.now().minus(1, ChronoUnit.DAYS),
                validTo = null,
                data = data,
                mediaType = "text/plain",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.id shouldBe entry.id
            created.branchId shouldBe mainBranchId
            created.dataType shouldBe "documents"
            created.name shouldBe "test-doc"
            created.mediaType shouldBe "text/plain"
            created.sizeBytes shouldBe data.size.toLong()
            created.createdBy shouldBe "test-user"
            created.transactionTo shouldBe null
            created.data shouldBe data
        }

        "should version existing entries when creating new version" {
            val data1 = "Version 1".toByteArray()
            val entry1 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "versioned-doc",
                validFrom = Instant.now().minus(2, ChronoUnit.DAYS),
                validTo = null,
                data = data1,
                mediaType = "text/plain",
                createdBy = "user1"
            )
            repository.create(entry1)

            // Wait a bit to ensure different transaction times
            Thread.sleep(100)

            val data2 = "Version 2".toByteArray()
            val entry2 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "versioned-doc",
                validFrom = Instant.now().minus(1, ChronoUnit.DAYS),
                validTo = null,
                data = data2,
                mediaType = "text/plain",
                createdBy = "user2"
            )
            repository.create(entry2)

            // Check history - should have 2 versions
            val history = repository.getHistory(mainBranchId, "documents", "versioned-doc")
            history shouldHaveSize 2

            // First in list should be newest (ordered by transaction_from DESC)
            history[0].data shouldBe data2
            history[0].transactionTo shouldBe null // Current version

            history[1].data shouldBe data1
            history[1].transactionTo shouldNotBe null // Versioned
        }

        "should create entries with valid time ranges" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "time-range-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = Instant.parse("2024-12-31T23:59:59Z"),
                data = "Time range data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.validFrom shouldBe Instant.parse("2024-01-01T00:00:00Z")
            created.validTo shouldBe Instant.parse("2024-12-31T23:59:59Z")
        }

        "should handle binary data correctly" {
            val binaryData = ByteArray(256) { it.toByte() }
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "images",
                name = "binary-test",
                validFrom = Instant.now(),
                validTo = null,
                data = binaryData,
                mediaType = "application/octet-stream",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.data shouldBe binaryData
            created.sizeBytes shouldBe 256L
        }
    }

    "DataEntryRepository.findCurrent" - {
        "should find currently valid and current version entry" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "current-doc",
                validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                validTo = null,
                data = "Current data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findCurrent(mainBranchId, "documents", "current-doc")

            found shouldNotBe null
            found?.name shouldBe "current-doc"
            found?.data shouldBe "Current data".toByteArray()
        }

        "should return null for non-existent entry" {
            val found = repository.findCurrent(mainBranchId, "documents", "non-existent")

            found shouldBe null
        }

        "should not find entry that is not yet valid" {
            val futureEntry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "future-doc",
                validFrom = Instant.now().plus(1, ChronoUnit.DAYS),
                validTo = null,
                data = "Future data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(futureEntry)

            val found = repository.findCurrent(mainBranchId, "documents", "future-doc")

            found shouldBe null
        }

        "should not find entry that is no longer valid" {
            val pastEntry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "past-doc",
                validFrom = Instant.now().minus(2, ChronoUnit.DAYS),
                validTo = Instant.now().minus(1, ChronoUnit.DAYS),
                data = "Past data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(pastEntry)

            val found = repository.findCurrent(mainBranchId, "documents", "past-doc")

            found shouldBe null
        }
    }

    "DataEntryRepository.findAsOfValidTime" - {
        "should find entry valid at a specific time" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "valid-time-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = Instant.parse("2024-12-31T23:59:59Z"),
                data = "2024 data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "valid-time-doc",
                Instant.parse("2024-06-01T12:00:00Z")
            )

            found shouldNotBe null
            found?.data shouldBe "2024 data".toByteArray()
        }

        "should not find entry before valid time" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "before-valid-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "before-valid-doc",
                Instant.parse("2023-12-31T23:59:59Z")
            )

            found shouldBe null
        }

        "should not find entry after valid time" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "after-valid-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = Instant.parse("2024-06-30T23:59:59Z"),
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "after-valid-doc",
                Instant.parse("2024-07-01T00:00:00Z")
            )

            found shouldBe null
        }
    }

    "DataEntryRepository.findAsOfTransactionTime" - {
        "should find entry as it was known at a specific transaction time" {
            val now = Instant.now()

            val entry1 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "transaction-time-doc",
                validFrom = now.minus(1, ChronoUnit.DAYS),
                validTo = null,
                data = "Version 1".toByteArray(),
                mediaType = "text/plain",
                createdBy = "user1"
            )
            repository.create(entry1)

            val afterFirstCreate = Instant.now()
            Thread.sleep(100)

            val entry2 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "transaction-time-doc",
                validFrom = now.minus(1, ChronoUnit.DAYS),
                validTo = null,
                data = "Version 2".toByteArray(),
                mediaType = "text/plain",
                createdBy = "user2"
            )
            repository.create(entry2)

            // Query as of after first create - should get version 1
            val found = repository.findAsOfTransactionTime(
                mainBranchId,
                "documents",
                "transaction-time-doc",
                afterFirstCreate
            )

            found shouldNotBe null
            found?.data shouldBe "Version 1".toByteArray()
        }

        "should return null if entry didn't exist at transaction time" {
            val beforeCreate = Instant.now()
            Thread.sleep(100)

            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "after-transaction-doc",
                validFrom = Instant.now(),
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findAsOfTransactionTime(
                mainBranchId,
                "documents",
                "after-transaction-doc",
                beforeCreate
            )

            found shouldBe null
        }
    }

    "DataEntryRepository.findBitemporal" - {
        "should perform full bitemporal query" {
            // Create entry valid in 2024
            val entry1 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "bitemporal-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = Instant.parse("2024-12-31T23:59:59Z"),
                data = "2024 version".toByteArray(),
                mediaType = "text/plain",
                createdBy = "user1"
            )
            repository.create(entry1)

            val afterFirstCreate = Instant.now()
            Thread.sleep(100)

            // Create entry valid in 2025
            val entry2 = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "bitemporal-doc",
                validFrom = Instant.parse("2025-01-01T00:00:00Z"),
                validTo = null,
                data = "2025 version".toByteArray(),
                mediaType = "text/plain",
                createdBy = "user2"
            )
            repository.create(entry2)

            // Query: What did we know after first create about what was valid in 2024?
            val found = repository.findBitemporal(
                mainBranchId,
                "documents",
                "bitemporal-doc",
                validTimeAsOf = Instant.parse("2024-06-01T00:00:00Z"),
                transactionTimeAsOf = afterFirstCreate
            )

            found shouldNotBe null
            found?.data shouldBe "2024 version".toByteArray()
        }

        "should return null if no entry matches both time dimensions" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "no-match-doc",
                validFrom = Instant.parse("2024-01-01T00:00:00Z"),
                validTo = Instant.parse("2024-12-31T23:59:59Z"),
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            // Query for 2025 (not valid) with current transaction time
            val found = repository.findBitemporal(
                mainBranchId,
                "documents",
                "no-match-doc",
                validTimeAsOf = Instant.parse("2025-06-01T00:00:00Z"),
                transactionTimeAsOf = Instant.now()
            )

            found shouldBe null
        }
    }

    "DataEntryRepository.listCurrent" - {
        "should list all current entries in a branch" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "list-doc-1",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Doc 1".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "images",
                    name = "list-img-1",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Img 1".toByteArray(),
                    mediaType = "image/png",
                    createdBy = "user2"
                )
            )

            val entries = repository.listCurrent(mainBranchId)

            val names = entries.map { it.name }
            names shouldContain "list-doc-1"
            names shouldContain "list-img-1"
        }

        "should filter by data type" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "filter-doc-1",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Doc".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "images",
                    name = "filter-img-1",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Img".toByteArray(),
                    mediaType = "image/png",
                    createdBy = "user2"
                )
            )

            val documents = repository.listCurrent(mainBranchId, "documents")

            documents.all { it.dataType == "documents" } shouldBe true
            documents.map { it.name } shouldContain "filter-doc-1"
        }

        "should not include future entries" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "future-list-doc",
                    validFrom = Instant.now().plus(1, ChronoUnit.DAYS),
                    validTo = null,
                    data = "Future".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )

            val entries = repository.listCurrent(mainBranchId)

            entries.none { it.name == "future-list-doc" } shouldBe true
        }

        "should not include past entries" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "past-list-doc",
                    validFrom = Instant.now().minus(2, ChronoUnit.DAYS),
                    validTo = Instant.now().minus(1, ChronoUnit.DAYS),
                    data = "Past".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )

            val entries = repository.listCurrent(mainBranchId)

            entries.none { it.name == "past-list-doc" } shouldBe true
        }
    }

    "DataEntryRepository.getHistory" - {
        "should return all versions of an entry" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "history-doc",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Version 1".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )
            Thread.sleep(100)
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "history-doc",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Version 2".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user2"
                )
            )
            Thread.sleep(100)
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "history-doc",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Version 3".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user3"
                )
            )

            val history = repository.getHistory(mainBranchId, "documents", "history-doc")

            history shouldHaveSize 3
            // Should be ordered by transaction_from DESC
            history[0].data shouldBe "Version 3".toByteArray()
            history[1].data shouldBe "Version 2".toByteArray()
            history[2].data shouldBe "Version 1".toByteArray()
        }

        "should return empty list for non-existent entry" {
            val history = repository.getHistory(mainBranchId, "documents", "non-existent-history")

            history shouldHaveSize 0
        }
    }

    "DataEntryRepository.delete" - {
        "should soft delete an entry by setting transaction_to" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "delete-doc",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Delete me".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )

            val deleted = repository.delete(mainBranchId, "documents", "delete-doc")

            deleted shouldBe true

            // Should not be found by findCurrent
            val found = repository.findCurrent(mainBranchId, "documents", "delete-doc")
            found shouldBe null

            // Should still be in history
            val history = repository.getHistory(mainBranchId, "documents", "delete-doc")
            history shouldHaveSize 1
            history[0].transactionTo shouldNotBe null
        }

        "should return false for non-existent entry" {
            val deleted = repository.delete(mainBranchId, "documents", "non-existent-delete")

            deleted shouldBe false
        }

        "should return false for already deleted entry" {
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "already-deleted-doc",
                    validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Data".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "test-user"
                )
            )
            repository.delete(mainBranchId, "documents", "already-deleted-doc")

            val deletedAgain = repository.delete(mainBranchId, "documents", "already-deleted-doc")

            deletedAgain shouldBe false
        }
    }

    "DataEntryRepository - Boundary Conditions" - {
        "should find entry at exact valid_from timestamp" {
            val validFrom = Instant.parse("2024-06-01T00:00:00Z")
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "boundary-valid-from",
                validFrom = validFrom,
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            // Query at exact valid_from time
            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "boundary-valid-from",
                validFrom
            )

            found shouldNotBe null
            found?.data shouldBe "Data".toByteArray()
        }

        "should not find entry at exact valid_to timestamp" {
            val validFrom = Instant.parse("2024-01-01T00:00:00Z")
            val validTo = Instant.parse("2024-12-31T23:59:59Z")
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "boundary-valid-to",
                validFrom = validFrom,
                validTo = validTo,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            // Query at exact valid_to time - should not find (exclusive upper bound)
            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "boundary-valid-to",
                validTo
            )

            found shouldBe null
        }

        "should find entry one second before valid_to" {
            val validFrom = Instant.parse("2024-01-01T00:00:00Z")
            val validTo = Instant.parse("2024-12-31T23:59:59Z")
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "boundary-before-valid-to",
                validFrom = validFrom,
                validTo = validTo,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            // Query one second before valid_to - should find
            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "boundary-before-valid-to",
                validTo.minus(1, ChronoUnit.SECONDS)
            )

            found shouldNotBe null
        }

        "should handle millisecond precision in timestamps" {
            val validFrom = Instant.parse("2024-06-01T12:34:56.789Z")
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "boundary-milliseconds",
                validFrom = validFrom,
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )
            repository.create(entry)

            val found = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "boundary-milliseconds",
                validFrom.plus(1, ChronoUnit.MILLIS)
            )

            found shouldNotBe null
        }

        "should correctly handle transaction time boundaries" {
            val now = Instant.now()
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "boundary-transaction-time",
                validFrom = now.minus(1, ChronoUnit.HOURS),
                validTo = null,
                data = "Version 1".toByteArray(),
                mediaType = "text/plain",
                createdBy = "user1"
            )
            repository.create(entry)

            val afterFirstCreate = Instant.now()
            Thread.sleep(50)

            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "boundary-transaction-time",
                    validFrom = now.minus(1, ChronoUnit.HOURS),
                    validTo = null,
                    data = "Version 2".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user2"
                )
            )

            // Query just after first create should get version 1
            val found = repository.findAsOfTransactionTime(
                mainBranchId,
                "documents",
                "boundary-transaction-time",
                afterFirstCreate
            )

            found shouldNotBe null
            found?.data shouldBe "Version 1".toByteArray()
        }
    }

    "DataEntryRepository - Input Validation & Edge Cases" - {
        "should handle special characters in dataType" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "my-data_type.v1",
                name = "special-char-type",
                validFrom = Instant.now(),
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.dataType shouldBe "my-data_type.v1"
        }

        "should handle special characters in name" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "file_name-v1.2.3",
                validFrom = Instant.now(),
                validTo = null,
                data = "Data".toByteArray(),
                mediaType = "text/plain",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.name shouldBe "file_name-v1.2.3"
        }

        "should handle large binary data (1MB)" {
            val largeData = ByteArray(1024 * 1024) { (it % 256).toByte() } // 1MB
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "documents",
                name = "large-file",
                validFrom = Instant.now(),
                validTo = null,
                data = largeData,
                mediaType = "application/octet-stream",
                createdBy = "test-user"
            )

            val created = repository.create(entry)

            created.data shouldBe largeData
            created.sizeBytes shouldBe (1024L * 1024L)
        }

        "should handle Unicode in metadata" {
            val entry = DataEntry.create(
                branchId = mainBranchId,
                dataType = "文档", // "documents" in Chinese
                name = "ファイル", // "file" in Japanese
                validFrom = Instant.now(),
                validTo = null,
                data = "Unicode data: 你好世界 🌍".toByteArray(Charsets.UTF_8),
                mediaType = "text/plain; charset=utf-8",
                createdBy = "用户" // "user" in Chinese
            )

            val created = repository.create(entry)

            created.dataType shouldBe "文档"
            created.name shouldBe "ファイル"
            created.createdBy shouldBe "用户"
        }
    }

    "DataEntryRepository - Complex Scenarios" - {
        "should handle multiple overlapping valid time ranges" {
            val baseTime = Instant.parse("2024-01-01T00:00:00Z")

            // Version 1: valid for Jan-Mar 2024
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "overlapping-doc",
                    validFrom = baseTime,
                    validTo = baseTime.plus(90, ChronoUnit.DAYS),
                    data = "Q1 version".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )

            val afterV1 = Instant.now()
            Thread.sleep(50)

            // Version 2: valid for Apr-Jun 2024
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "overlapping-doc",
                    validFrom = baseTime.plus(90, ChronoUnit.DAYS),
                    validTo = baseTime.plus(180, ChronoUnit.DAYS),
                    data = "Q2 version".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user2"
                )
            )

            // Query in Q1 period - use bitemporal query with afterV1 transaction time
            val foundQ1 = repository.findBitemporal(
                mainBranchId,
                "documents",
                "overlapping-doc",
                validTimeAsOf = baseTime.plus(45, ChronoUnit.DAYS),
                transactionTimeAsOf = afterV1
            )
            foundQ1?.data shouldBe "Q1 version".toByteArray()

            // Query in Q2 period - use current transaction time
            val foundQ2 = repository.findAsOfValidTime(
                mainBranchId,
                "documents",
                "overlapping-doc",
                baseTime.plus(135, ChronoUnit.DAYS)
            )
            foundQ2?.data shouldBe "Q2 version".toByteArray()
        }

        "should handle many versions efficiently (20+ versions)" {
            // Create 20 versions
            repeat(20) { i ->
                repository.create(
                    DataEntry.create(
                        branchId = mainBranchId,
                        dataType = "documents",
                        name = "many-versions-doc",
                        validFrom = Instant.now().minus(1, ChronoUnit.HOURS),
                        validTo = null,
                        data = "Version ${i + 1}".toByteArray(),
                        mediaType = "text/plain",
                        createdBy = "user-$i"
                    )
                )
                if (i < 19) Thread.sleep(10) // Small delay between versions
            }

            val history = repository.getHistory(mainBranchId, "documents", "many-versions-doc")

            history shouldHaveSize 20
            history[0].data shouldBe "Version 20".toByteArray() // Most recent
            history[19].data shouldBe "Version 1".toByteArray() // Oldest
        }

        "should correctly version when valid time changes" {
            val now = Instant.now()

            // Version 1: valid from yesterday
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "changing-valid-time",
                    validFrom = now.minus(1, ChronoUnit.DAYS),
                    validTo = null,
                    data = "Old validity".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user1"
                )
            )

            Thread.sleep(50)

            // Version 2: valid from today (narrower valid time range)
            repository.create(
                DataEntry.create(
                    branchId = mainBranchId,
                    dataType = "documents",
                    name = "changing-valid-time",
                    validFrom = now,
                    validTo = null,
                    data = "New validity".toByteArray(),
                    mediaType = "text/plain",
                    createdBy = "user2"
                )
            )

            // Current query should get version 2
            val current = repository.findCurrent(mainBranchId, "documents", "changing-valid-time")
            current?.data shouldBe "New validity".toByteArray()

            // Both versions should be in history
            val history = repository.getHistory(mainBranchId, "documents", "changing-valid-time")
            history shouldHaveSize 2
        }
    }
})
