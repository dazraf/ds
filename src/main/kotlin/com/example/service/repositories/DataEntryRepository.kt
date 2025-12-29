package com.example.service.repositories

import com.example.service.models.DataEntry
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Repository for managing bitemporal data entries within a namespace database.
 *
 * Supports full bitemporal querying across two time dimensions:
 * - Valid time: When the data was true in the real world
 * - Transaction time: When the data was recorded in the database
 *
 * This allows queries like:
 * - "What is currently valid and known?" (current snapshot)
 * - "What was valid on date X?" (historical valid time)
 * - "What did we know on date Y?" (historical transaction time)
 * - "What did we know on date Y about what was valid on date X?" (full bitemporal)
 *
 * @property pool Connection pool to the namespace database
 */
class DataEntryRepository(
    private val pool: Pool
) {
    /**
     * Creates a new data entry.
     *
     * If an entry with the same branch_id, data_type, and name already exists and is current,
     * it will be versioned by setting its transaction_to timestamp.
     *
     * @param entry The data entry to create
     * @return The created data entry
     */
    suspend fun create(entry: DataEntry): DataEntry {
        // First, version any existing current entries with same branch_id, data_type, name
        versionExistingEntries(entry.branchId, entry.dataType, entry.name)

        val query = """
            INSERT INTO data_entries (
                id, branch_id, data_type, name,
                valid_from, valid_to, transaction_from, transaction_to,
                data, media_type, size_bytes, created_by
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
            RETURNING id, branch_id, data_type, name,
                      valid_from, valid_to, transaction_from, transaction_to,
                      data, media_type, size_bytes, created_by
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(
                Tuple.of(
                    entry.id,
                    entry.branchId,
                    entry.dataType,
                    entry.name,
                    OffsetDateTime.ofInstant(entry.validFrom, ZoneOffset.UTC),
                    entry.validTo?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) } ?: OffsetDateTime.MAX,
                    OffsetDateTime.ofInstant(entry.transactionFrom, ZoneOffset.UTC),
                    entry.transactionTo?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) } ?: OffsetDateTime.MAX,
                    io.vertx.core.buffer.Buffer.buffer(entry.data),
                    entry.mediaType,
                    entry.sizeBytes,
                    entry.createdBy
                )
            )
            .coAwait()

        return rowToDataEntry(result.first())
    }

    /**
     * Versions existing current entries by setting their transaction_to timestamp.
     *
     * This is called before creating a new version of a data entry.
     */
    private suspend fun versionExistingEntries(branchId: UUID, dataType: String, name: String) {
        val query = """
            UPDATE data_entries
            SET transaction_to = NOW()
            WHERE branch_id = $1
              AND data_type = $2
              AND name = $3
              AND transaction_to = 'infinity'::TIMESTAMPTZ
        """.trimIndent()

        pool.preparedQuery(query)
            .execute(Tuple.of(branchId, dataType, name))
            .coAwait()
    }

    /**
     * Finds the current data entry for a given branch, data type, and name.
     *
     * "Current" means both currently valid and the current version in the database.
     *
     * @param branchId Branch to search in
     * @param dataType Data type to search for
     * @param name Name to search for
     * @return The current data entry if found, null otherwise
     */
    suspend fun findCurrent(branchId: UUID, dataType: String, name: String): DataEntry? {
        return findBitemporal(branchId, dataType, name, Instant.now(), Instant.now())
    }

    /**
     * Finds a data entry as it was valid at a specific time.
     *
     * Uses the current transaction time (i.e., the latest known version).
     *
     * @param branchId Branch to search in
     * @param dataType Data type to search for
     * @param name Name to search for
     * @param validTimeAsOf The valid time to query at
     * @return The data entry if found, null otherwise
     */
    suspend fun findAsOfValidTime(
        branchId: UUID,
        dataType: String,
        name: String,
        validTimeAsOf: Instant
    ): DataEntry? {
        return findBitemporal(branchId, dataType, name, validTimeAsOf, Instant.now())
    }

    /**
     * Finds a data entry as it was known at a specific transaction time.
     *
     * Uses the current valid time (i.e., currently valid data).
     *
     * @param branchId Branch to search in
     * @param dataType Data type to search for
     * @param name Name to search for
     * @param transactionTimeAsOf The transaction time to query at
     * @return The data entry if found, null otherwise
     */
    suspend fun findAsOfTransactionTime(
        branchId: UUID,
        dataType: String,
        name: String,
        transactionTimeAsOf: Instant
    ): DataEntry? {
        return findBitemporal(branchId, dataType, name, Instant.now(), transactionTimeAsOf)
    }

    /**
     * Finds a data entry using full bitemporal query.
     *
     * Searches for data that was valid at a specific time AND was known at a specific time.
     *
     * @param branchId Branch to search in
     * @param dataType Data type to search for
     * @param name Name to search for
     * @param validTimeAsOf The valid time to query at
     * @param transactionTimeAsOf The transaction time to query at
     * @return The data entry if found, null otherwise
     */
    suspend fun findBitemporal(
        branchId: UUID,
        dataType: String,
        name: String,
        validTimeAsOf: Instant,
        transactionTimeAsOf: Instant
    ): DataEntry? {
        val query = """
            SELECT id, branch_id, data_type, name,
                   valid_from, valid_to, transaction_from, transaction_to,
                   data, media_type, size_bytes, created_by
            FROM data_entries
            WHERE branch_id = $1
              AND data_type = $2
              AND name = $3
              AND valid_from <= $4
              AND (valid_to > $4 OR valid_to IS NULL)
              AND transaction_from <= $5
              AND (transaction_to > $5 OR transaction_to IS NULL)
            ORDER BY transaction_from DESC
            LIMIT 1
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(
                Tuple.of(
                    branchId,
                    dataType,
                    name,
                    OffsetDateTime.ofInstant(validTimeAsOf, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(transactionTimeAsOf, ZoneOffset.UTC)
                )
            )
            .coAwait()

        if (result.size() == 0) {
            return null
        }

        return rowToDataEntry(result.first())
    }

    /**
     * Lists all current data entries in a branch.
     *
     * @param branchId Branch to list entries from
     * @param dataType Optional filter by data type
     * @return List of current data entries
     */
    suspend fun listCurrent(branchId: UUID, dataType: String? = null): List<DataEntry> {
        val query = if (dataType != null) {
            """
                SELECT id, branch_id, data_type, name,
                       valid_from, valid_to, transaction_from, transaction_to,
                       data, media_type, size_bytes, created_by
                FROM data_entries
                WHERE branch_id = $1
                  AND data_type = $2
                  AND valid_from <= NOW()
                  AND (valid_to > NOW() OR valid_to IS NULL)
                  AND transaction_from <= NOW()
                  AND (transaction_to > NOW() OR transaction_to IS NULL)
                ORDER BY data_type, name, transaction_from DESC
            """.trimIndent()
        } else {
            """
                SELECT id, branch_id, data_type, name,
                       valid_from, valid_to, transaction_from, transaction_to,
                       data, media_type, size_bytes, created_by
                FROM data_entries
                WHERE branch_id = $1
                  AND valid_from <= NOW()
                  AND (valid_to > NOW() OR valid_to IS NULL)
                  AND transaction_from <= NOW()
                  AND (transaction_to > NOW() OR transaction_to IS NULL)
                ORDER BY data_type, name, transaction_from DESC
            """.trimIndent()
        }

        val result = if (dataType != null) {
            pool.preparedQuery(query)
                .execute(Tuple.of(branchId, dataType))
                .coAwait()
        } else {
            pool.preparedQuery(query)
                .execute(Tuple.of(branchId))
                .coAwait()
        }

        return result.map { row -> rowToDataEntry(row) }
    }

    /**
     * Gets the full history of a data entry (all versions).
     *
     * @param branchId Branch to search in
     * @param dataType Data type to search for
     * @param name Name to search for
     * @return List of all versions, ordered by transaction time descending
     */
    suspend fun getHistory(branchId: UUID, dataType: String, name: String): List<DataEntry> {
        val query = """
            SELECT id, branch_id, data_type, name,
                   valid_from, valid_to, transaction_from, transaction_to,
                   data, media_type, size_bytes, created_by
            FROM data_entries
            WHERE branch_id = $1
              AND data_type = $2
              AND name = $3
            ORDER BY transaction_from DESC
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(branchId, dataType, name))
            .coAwait()

        return result.map { row -> rowToDataEntry(row) }
    }

    /**
     * Deletes (versions) a data entry by setting its transaction_to timestamp.
     *
     * This is a soft delete that preserves history.
     *
     * @param branchId Branch the entry belongs to
     * @param dataType Data type of the entry
     * @param name Name of the entry
     * @return true if the entry was deleted, false if it didn't exist or was already deleted
     */
    suspend fun delete(branchId: UUID, dataType: String, name: String): Boolean {
        val query = """
            UPDATE data_entries
            SET transaction_to = NOW()
            WHERE branch_id = $1
              AND data_type = $2
              AND name = $3
              AND transaction_to = 'infinity'::TIMESTAMPTZ
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(branchId, dataType, name))
            .coAwait()

        return result.rowCount() > 0
    }

    /**
     * Converts a database row to a DataEntry domain object.
     *
     * Maps PostgreSQL 'infinity' timestamps (OffsetDateTime.MAX) to null in the domain model.
     */
    private fun rowToDataEntry(row: io.vertx.sqlclient.Row): DataEntry {
        val validTo = row.getOffsetDateTime("valid_to")
        val transactionTo = row.getOffsetDateTime("transaction_to")

        // Map OffsetDateTime.MAX (infinity) to null
        val validToInstant = validTo?.let {
            if (it == OffsetDateTime.MAX || it.year > 9000) null else it.toInstant()
        }
        val transactionToInstant = transactionTo?.let {
            if (it == OffsetDateTime.MAX || it.year > 9000) null else it.toInstant()
        }

        return DataEntry(
            id = row.getUUID("id"),
            branchId = row.getUUID("branch_id"),
            dataType = row.getString("data_type"),
            name = row.getString("name"),
            validFrom = row.getOffsetDateTime("valid_from").toInstant(),
            validTo = validToInstant,
            transactionFrom = row.getOffsetDateTime("transaction_from").toInstant(),
            transactionTo = transactionToInstant,
            data = row.getBuffer("data").bytes,
            mediaType = row.getString("media_type"),
            sizeBytes = row.getLong("size_bytes"),
            createdBy = row.getString("created_by")
        )
    }
}
