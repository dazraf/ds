package com.example.service.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * Request DTO for uploading data (metadata portion).
 *
 * Used in multipart/form-data uploads where the binary data is
 * in a separate file part.
 */
data class UploadDataRequest(
    @field:JsonProperty("validFrom")
    val validFrom: Instant? = null,

    @field:JsonProperty("validTo")
    val validTo: Instant? = null,

    @field:JsonProperty("mediaType")
    val mediaType: String? = null,

    @field:JsonProperty("tags")
    val tags: List<String> = emptyList()
)

/**
 * Response DTO for data entry metadata (without binary data).
 */
data class DataEntryMetadataResponse(
    @JsonProperty("id")
    val id: UUID,

    @JsonProperty("branchId")
    val branchId: UUID,

    @JsonProperty("dataType")
    val dataType: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("validFrom")
    val validFrom: Instant,

    @JsonProperty("validTo")
    val validTo: Instant?,

    @JsonProperty("transactionFrom")
    val transactionFrom: Instant,

    @JsonProperty("transactionTo")
    val transactionTo: Instant?,

    @JsonProperty("mediaType")
    val mediaType: String,

    @JsonProperty("sizeBytes")
    val sizeBytes: Long,

    @JsonProperty("createdBy")
    val createdBy: String,

    @JsonProperty("tags")
    val tags: List<String> = emptyList()
) {
    companion object {
        fun from(entry: DataEntry, tags: List<Tag> = emptyList()): DataEntryMetadataResponse {
            return DataEntryMetadataResponse(
                id = entry.id,
                branchId = entry.branchId,
                dataType = entry.dataType,
                name = entry.name,
                validFrom = entry.validFrom,
                validTo = entry.validTo,
                transactionFrom = entry.transactionFrom,
                transactionTo = entry.transactionTo,
                mediaType = entry.mediaType,
                sizeBytes = entry.sizeBytes,
                createdBy = entry.createdBy,
                tags = tags.map { it.value }.sorted()
            )
        }
    }
}

/**
 * Response DTO for listing data entries.
 */
data class ListDataEntriesResponse(
    @JsonProperty("entries")
    val entries: List<DataEntryMetadataResponse>
)

/**
 * Response DTO for data entry history.
 */
data class DataEntryHistoryResponse(
    @JsonProperty("history")
    val history: List<DataEntryMetadataResponse>
)

/**
 * Request DTO for adding tags.
 */
data class AddTagsRequest(
    @field:JsonProperty("tags")
    val tags: List<String>
) {
    init {
        require(tags.isNotEmpty()) {
            "At least one tag must be provided"
        }
        tags.forEach { tag ->
            require(tag.isNotBlank()) {
                "Tags cannot be blank"
            }
            require(tag.length <= 255) {
                "Tag length cannot exceed 255 characters"
            }
        }
    }
}

/**
 * Response DTO for tags.
 */
data class TagsResponse(
    @JsonProperty("tags")
    val tags: List<String>
)
