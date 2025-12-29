package com.example.service.models

import java.util.UUID

/**
 * Domain model for a tag associated with a data entry.
 *
 * Tags are simple string values that can be attached to data entries
 * to enable categorization and searching. Tags are versioned along
 * with the data entries they reference.
 *
 * @property id Unique identifier for the tag
 * @property dataEntryId The data entry this tag is attached to
 * @property value The tag value (simple string, not key-value pairs)
 */
data class Tag(
    val id: UUID,
    val dataEntryId: UUID,
    val value: String
) {
    companion object {
        /**
         * Creates a new tag instance.
         *
         * @param dataEntryId The data entry to tag
         * @param value The tag value
         * @return New Tag instance
         */
        fun create(
            dataEntryId: UUID,
            value: String
        ): Tag {
            require(value.isNotBlank()) { "Tag value cannot be blank" }
            require(value.length <= 255) { "Tag value cannot exceed 255 characters" }

            return Tag(
                id = UUID.randomUUID(),
                dataEntryId = dataEntryId,
                value = value
            )
        }
    }
}
