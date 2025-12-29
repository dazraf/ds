package com.example.service.models

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a bitemporal data entry.
 *
 * DataEntry represents a piece of opaque binary data with bitemporal semantics:
 * - Valid time: When the data is true in the real world
 * - Transaction time: When the data was recorded in the database
 *
 * This allows querying data "as it was known at time X" and "as it was true at time Y".
 *
 * @property id Unique identifier for the data entry
 * @property branchId Branch this entry belongs to
 * @property dataType Logical data type (e.g., "documents", "images", "contracts")
 * @property name Unique name within data_type and branch
 * @property validFrom Valid time: when this data became true in the real world
 * @property validTo Valid time: when this data stopped being true (null means current)
 * @property transactionFrom Transaction time: when this data was recorded in the database
 * @property transactionTo Transaction time: when this data was superseded (null means current)
 * @property data Opaque binary data
 * @property mediaType MIME type / content type of the data
 * @property sizeBytes Size of data in bytes
 * @property createdBy User who created this entry
 */
data class DataEntry(
    val id: UUID,
    val branchId: UUID,
    val dataType: String,
    val name: String,
    val validFrom: Instant,
    val validTo: Instant?,
    val transactionFrom: Instant,
    val transactionTo: Instant?,
    val data: ByteArray,
    val mediaType: String,
    val sizeBytes: Long,
    val createdBy: String
) {
    /**
     * Whether this data entry is currently valid (in valid time).
     */
    fun isCurrentlyValid(asOf: Instant = Instant.now()): Boolean {
        return asOf >= validFrom && (validTo == null || asOf < validTo)
    }

    /**
     * Whether this data entry is the current version in the database (in transaction time).
     */
    fun isCurrentVersion(asOf: Instant = Instant.now()): Boolean {
        return asOf >= transactionFrom && (transactionTo == null || asOf < transactionTo)
    }

    /**
     * Whether this data entry is both currently valid and the current version.
     */
    fun isCurrent(validTimeAsOf: Instant = Instant.now(), transactionTimeAsOf: Instant = Instant.now()): Boolean {
        return isCurrentlyValid(validTimeAsOf) && isCurrentVersion(transactionTimeAsOf)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataEntry

        if (id != other.id) return false
        if (branchId != other.branchId) return false
        if (dataType != other.dataType) return false
        if (name != other.name) return false
        if (validFrom != other.validFrom) return false
        if (validTo != other.validTo) return false
        if (transactionFrom != other.transactionFrom) return false
        if (transactionTo != other.transactionTo) return false
        if (!data.contentEquals(other.data)) return false
        if (mediaType != other.mediaType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (createdBy != other.createdBy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + branchId.hashCode()
        result = 31 * result + dataType.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + (validTo?.hashCode() ?: 0)
        result = 31 * result + transactionFrom.hashCode()
        result = 31 * result + (transactionTo?.hashCode() ?: 0)
        result = 31 * result + data.contentHashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + createdBy.hashCode()
        return result
    }

    companion object {
        /**
         * Creates a new data entry instance.
         *
         * @param branchId Branch this entry belongs to
         * @param dataType Logical data type
         * @param name Unique name within data_type and branch
         * @param validFrom When this data became valid in the real world
         * @param validTo When this data stopped being valid (null for current)
         * @param data Binary data payload
         * @param mediaType MIME type of the data
         * @param createdBy User creating this entry
         * @return New DataEntry instance with transaction time set to now
         */
        fun create(
            branchId: UUID,
            dataType: String,
            name: String,
            validFrom: Instant,
            validTo: Instant?,
            data: ByteArray,
            mediaType: String,
            createdBy: String
        ): DataEntry {
            require(dataType.isNotBlank()) { "dataType cannot be blank" }
            require(name.isNotBlank()) { "name cannot be blank" }
            require(data.isNotEmpty()) { "data cannot be empty" }
            require(mediaType.isNotBlank()) { "mediaType cannot be blank" }
            if (validTo != null) {
                require(validFrom < validTo) { "validFrom must be before validTo" }
            }

            return DataEntry(
                id = UUID.randomUUID(),
                branchId = branchId,
                dataType = dataType,
                name = name,
                validFrom = validFrom,
                validTo = validTo,
                transactionFrom = Instant.now(),
                transactionTo = null,
                data = data,
                mediaType = mediaType,
                sizeBytes = data.size.toLong(),
                createdBy = createdBy
            )
        }
    }
}
