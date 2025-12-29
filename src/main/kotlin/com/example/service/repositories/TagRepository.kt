package com.example.service.repositories

import com.example.service.models.Tag
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import java.util.UUID

/**
 * Repository for managing tags associated with data entries.
 *
 * Tags are simple string values that enable categorization and searching.
 * They are versioned along with the data entries they reference.
 *
 * @property pool Connection pool to the namespace database
 */
class TagRepository(
    private val pool: Pool
) {
    /**
     * Creates a new tag for a data entry.
     *
     * @param tag The tag to create
     * @return The created tag
     * @throws TagAlreadyExistsException if the tag already exists for this data entry
     */
    suspend fun create(tag: Tag): Tag {
        val query = """
            INSERT INTO tags (id, data_entry_id, value)
            VALUES ($1, $2, $3)
            RETURNING id, data_entry_id, value
        """.trimIndent()

        try {
            val result = pool.preparedQuery(query)
                .execute(
                    Tuple.of(
                        tag.id,
                        tag.dataEntryId,
                        tag.value
                    )
                )
                .coAwait()

            val row = result.first()
            return Tag(
                id = row.getUUID("id"),
                dataEntryId = row.getUUID("data_entry_id"),
                value = row.getString("value")
            )
        } catch (e: Exception) {
            if (e.message?.contains("duplicate key value violates unique constraint") == true) {
                throw TagAlreadyExistsException("Tag '${tag.value}' already exists for this data entry")
            }
            throw e
        }
    }

    /**
     * Finds all tags for a specific data entry.
     *
     * @param dataEntryId The data entry ID to search for
     * @return List of tags associated with the data entry
     */
    suspend fun findByDataEntryId(dataEntryId: UUID): List<Tag> {
        val query = """
            SELECT id, data_entry_id, value
            FROM tags
            WHERE data_entry_id = $1
            ORDER BY value
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(dataEntryId))
            .coAwait()

        return result.map { row ->
            Tag(
                id = row.getUUID("id"),
                dataEntryId = row.getUUID("data_entry_id"),
                value = row.getString("value")
            )
        }
    }

    /**
     * Searches for data entry IDs that have a specific tag value.
     *
     * @param value The tag value to search for
     * @return List of data entry IDs that have this tag
     */
    suspend fun findDataEntriesByTag(value: String): List<UUID> {
        val query = """
            SELECT data_entry_id
            FROM tags
            WHERE value = $1
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(value))
            .coAwait()

        return result.map { row ->
            row.getUUID("data_entry_id")
        }
    }

    /**
     * Deletes a specific tag from a data entry.
     *
     * @param dataEntryId The data entry ID
     * @param value The tag value to delete
     * @return true if the tag was deleted, false if it didn't exist
     */
    suspend fun delete(dataEntryId: UUID, value: String): Boolean {
        val query = """
            DELETE FROM tags
            WHERE data_entry_id = $1 AND value = $2
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(dataEntryId, value))
            .coAwait()

        return result.rowCount() > 0
    }

    /**
     * Deletes all tags for a data entry.
     *
     * @param dataEntryId The data entry ID
     * @return Number of tags deleted
     */
    suspend fun deleteAllForDataEntry(dataEntryId: UUID): Int {
        val query = """
            DELETE FROM tags
            WHERE data_entry_id = $1
        """.trimIndent()

        val result = pool.preparedQuery(query)
            .execute(Tuple.of(dataEntryId))
            .coAwait()

        return result.rowCount()
    }
}

/**
 * Exception thrown when attempting to create a tag that already exists.
 */
class TagAlreadyExistsException(message: String) : Exception(message)
