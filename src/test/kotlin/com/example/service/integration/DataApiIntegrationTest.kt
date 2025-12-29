package com.example.service.integration

import com.example.service.MainVerticle
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.multipart.MultipartForm
import io.vertx.kotlin.coroutines.coAwait
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Comprehensive integration tests for Data API endpoints.
 *
 * Tests cover:
 * - Upload/download data with multipart forms
 * - Metadata operations
 * - Version history
 * - Temporal queries
 * - Tag management
 * - List and filtering
 */
class DataApiIntegrationTest : FreeSpec({
    val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("testdb")
        withUsername("postgres")
        withPassword("postgres")
    }

    val postgresListener = postgres.perSpec()
    listener(postgresListener)

    lateinit var vertx: Vertx
    lateinit var webClient: WebClient
    var port: Int = 8889  // Different port from NamespaceIntegrationTest

    beforeSpec {
        vertx = Vertx.vertx()

        val adminConfig = JsonObject()
            .put("host", postgres.host)
            .put("port", postgres.firstMappedPort)
            .put("database", "postgres")
            .put("user", "postgres")
            .put("password", "postgres")
            .put("maxPoolSize", 2)

        val registryConfig = JsonObject()
            .put("host", postgres.host)
            .put("port", postgres.firstMappedPort)
            .put("database", "testdb")
            .put("user", "postgres")
            .put("password", "postgres")
            .put("maxPoolSize", 10)

        val config = JsonObject()
            .put("http", JsonObject().put("port", port).put("host", "0.0.0.0"))
            .put("database", JsonObject()
                .put("admin", adminConfig)
                .put("registry", registryConfig))

        vertx.deployVerticle(MainVerticle(), io.vertx.core.DeploymentOptions().setConfig(config)).coAwait()

        webClient = WebClient.create(vertx)

        // Create a test namespace
        val createNamespaceRequest = JsonObject().put("name", "test-data-ns")
        webClient.post(port, "localhost", "/api/v1/namespaces")
            .sendJsonObject(createNamespaceRequest)
            .coAwait()
    }

    afterSpec {
        webClient.close()
        vertx.close().coAwait()
    }

    "Data Upload API" - {
        "POST should upload data with multipart form" {
            // Create a temporary file
            val tempFile = Files.createTempFile("test-", ".txt")
            val testContent = "Hello, World!"
            Files.write(tempFile, testContent.toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                val response = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/documents/test-doc")
                    .sendMultipartForm(form)
                    .coAwait()

                response.statusCode() shouldBe 201

                val body = response.bodyAsJsonObject()
                body.getString("name") shouldBe "test-doc"
                body.getString("dataType") shouldBe "documents"
                body.getString("mediaType") shouldBe "text/plain"
                body.getLong("sizeBytes") shouldBe testContent.length.toLong()
                body.getString("id") shouldNotBe null
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "POST should upload data with metadata" {
            val tempFile = Files.createTempFile("test-", ".json")
            val testContent = """{"message": "test"}"""
            Files.write(tempFile, testContent.toByteArray())

            try {
                val metadata = JsonObject()
                    .put("mediaType", "application/json")
                    .put("tags", JsonArray().add("important").add("v1"))

                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "application/json")
                    .attribute("metadata", metadata.encode())

                val response = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/configs/app-config")
                    .sendMultipartForm(form)
                    .coAwait()

                response.statusCode() shouldBe 201

                val body = response.bodyAsJsonObject()
                body.getString("name") shouldBe "app-config"
                body.getString("mediaType") shouldBe "application/json"
                body.getJsonArray("tags").size() shouldBe 2
                body.getJsonArray("tags").getString(0) shouldBe "important"
                body.getJsonArray("tags").getString(1) shouldBe "v1"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "POST should return 404 for non-existent namespace" {
            val tempFile = Files.createTempFile("test-", ".txt")
            Files.write(tempFile, "test".toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                val response = webClient.post(port, "localhost", "/api/v1/namespaces/non-existent/branches/main/data/docs/test")
                    .sendMultipartForm(form)
                    .coAwait()

                response.statusCode() shouldBe 404
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "POST should return 404 for non-existent branch" {
            val tempFile = Files.createTempFile("test-", ".txt")
            Files.write(tempFile, "test".toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                val response = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/non-existent/data/docs/test")
                    .sendMultipartForm(form)
                    .coAwait()

                response.statusCode() shouldBe 404
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "POST should version existing data when uploading new version" {
            val tempFile1 = Files.createTempFile("test-", ".txt")
            Files.write(tempFile1, "Version 1".toByteArray())

            val tempFile2 = Files.createTempFile("test-", ".txt")
            Files.write(tempFile2, "Version 2".toByteArray())

            try {
                // Upload version 1
                val form1 = MultipartForm.create()
                    .textFileUpload("file", tempFile1.fileName.toString(), tempFile1.toString(), "text/plain")

                val response1 = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/versioned/doc1")
                    .sendMultipartForm(form1)
                    .coAwait()

                response1.statusCode() shouldBe 201

                // Upload version 2
                val form2 = MultipartForm.create()
                    .textFileUpload("file", tempFile2.fileName.toString(), tempFile2.toString(), "text/plain")

                val response2 = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/versioned/doc1")
                    .sendMultipartForm(form2)
                    .coAwait()

                response2.statusCode() shouldBe 201

                // Verify history has 2 versions
                val historyResponse = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/versioned/doc1/history")
                    .send()
                    .coAwait()

                historyResponse.statusCode() shouldBe 200
                val history = historyResponse.bodyAsJsonObject().getJsonArray("history")
                history.size() shouldBe 2
            } finally {
                Files.deleteIfExists(tempFile1)
                Files.deleteIfExists(tempFile2)
            }
        }
    }

    "Data Download API" - {
        "GET should download binary data" {
            // First upload a file
            val tempFile = Files.createTempFile("test-download-", ".txt")
            val testContent = "Download this content!"
            Files.write(tempFile, testContent.toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/downloads/file1")
                    .sendMultipartForm(form)
                    .coAwait()

                // Download it
                val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/downloads/file1")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                response.getHeader("Content-Type") shouldBe "text/plain"
                response.bodyAsString() shouldBe testContent
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "GET should return 404 for non-existent data" {
            val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/downloads/non-existent")
                .send()
                .coAwait()

            response.statusCode() shouldBe 404
        }
    }

    "Data Metadata API" - {
        "GET metadata should return metadata without binary data" {
            // Upload a file first
            val tempFile = Files.createTempFile("test-metadata-", ".txt")
            val testContent = "Test metadata"
            Files.write(tempFile, testContent.toByteArray())

            try {
                val metadata = JsonObject()
                    .put("tags", JsonArray().add("meta-test"))

                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")
                    .attribute("metadata", metadata.encode())

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/metadata-test/file1")
                    .sendMultipartForm(form)
                    .coAwait()

                // Get metadata
                val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/metadata-test/file1/metadata")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val body = response.bodyAsJsonObject()
                body.getString("name") shouldBe "file1"
                body.getString("dataType") shouldBe "metadata-test"
                body.getLong("sizeBytes") shouldBe testContent.length.toLong()
                body.getJsonArray("tags").size() shouldBe 1
                body.getJsonArray("tags").getString(0) shouldBe "meta-test"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "GET metadata should return 404 for non-existent data" {
            val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/metadata-test/non-existent/metadata")
                .send()
                .coAwait()

            response.statusCode() shouldBe 404
        }
    }

    "Data History API" - {
        "GET history should return all versions" {
            // Upload multiple versions
            for (i in 1..3) {
                val tempFile = Files.createTempFile("test-history-", ".txt")
                Files.write(tempFile, "Version $i".toByteArray())

                try {
                    val form = MultipartForm.create()
                        .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                    webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/history-test/versioned-file")
                        .sendMultipartForm(form)
                        .coAwait()

                    // Small delay to ensure different transaction times
                    Thread.sleep(10)
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            // Get history
            val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/history-test/versioned-file/history")
                .send()
                .coAwait()

            response.statusCode() shouldBe 200
            val history = response.bodyAsJsonObject().getJsonArray("history")
            history.size() shouldBe 3

            // Should be ordered by transaction time descending (most recent first)
            val first = history.getJsonObject(0)
            first.getString("name") shouldBe "versioned-file"
        }

        "GET history should return empty list for non-existent data" {
            val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/history-test/non-existent/history")
                .send()
                .coAwait()

            response.statusCode() shouldBe 200
            val history = response.bodyAsJsonObject().getJsonArray("history")
            history.size() shouldBe 0
        }
    }

    "Data List API" - {
        "GET should list all current entries by type" {
            // Upload multiple files
            for (i in 1..3) {
                val tempFile = Files.createTempFile("test-list-", ".txt")
                Files.write(tempFile, "List test $i".toByteArray())

                try {
                    val form = MultipartForm.create()
                        .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                    webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/list-test/file-$i")
                        .sendMultipartForm(form)
                        .coAwait()
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            // List entries
            val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/list-test")
                .send()
                .coAwait()

            response.statusCode() shouldBe 200
            val entries = response.bodyAsJsonObject().getJsonArray("entries")
            entries.size() shouldBe 3
        }

        "GET should filter by tag" {
            // Upload files with different tags
            val tempFile1 = Files.createTempFile("test-filter-", ".txt")
            Files.write(tempFile1, "Tagged 1".toByteArray())

            val tempFile2 = Files.createTempFile("test-filter-", ".txt")
            Files.write(tempFile2, "Tagged 2".toByteArray())

            val tempFile3 = Files.createTempFile("test-filter-", ".txt")
            Files.write(tempFile3, "Not tagged".toByteArray())

            try {
                val metadata1 = JsonObject().put("tags", JsonArray().add("filter-test"))
                val form1 = MultipartForm.create()
                    .textFileUpload("file", tempFile1.fileName.toString(), tempFile1.toString(), "text/plain")
                    .attribute("metadata", metadata1.encode())

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/filter-test/tagged-1")
                    .sendMultipartForm(form1)
                    .coAwait()

                val metadata2 = JsonObject().put("tags", JsonArray().add("filter-test"))
                val form2 = MultipartForm.create()
                    .textFileUpload("file", tempFile2.fileName.toString(), tempFile2.toString(), "text/plain")
                    .attribute("metadata", metadata2.encode())

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/filter-test/tagged-2")
                    .sendMultipartForm(form2)
                    .coAwait()

                val form3 = MultipartForm.create()
                    .textFileUpload("file", tempFile3.fileName.toString(), tempFile3.toString(), "text/plain")

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/filter-test/not-tagged")
                    .sendMultipartForm(form3)
                    .coAwait()

                // Filter by tag
                val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/filter-test?tag=filter-test")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val entries = response.bodyAsJsonObject().getJsonArray("entries")
                entries.size() shouldBe 2

                val names = entries.map { (it as JsonObject).getString("name") }
                names shouldContain "tagged-1"
                names shouldContain "tagged-2"
            } finally {
                Files.deleteIfExists(tempFile1)
                Files.deleteIfExists(tempFile2)
                Files.deleteIfExists(tempFile3)
            }
        }
    }

    "Data Delete API" - {
        "DELETE should soft delete a data entry" {
            // Upload a file
            val tempFile = Files.createTempFile("test-delete-", ".txt")
            Files.write(tempFile, "Delete me".toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-test/delete-me")
                    .sendMultipartForm(form)
                    .coAwait()

                // Delete it
                val response = webClient.delete(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-test/delete-me")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 204

                // Verify it's not found
                val getResponse = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-test/delete-me")
                    .send()
                    .coAwait()

                getResponse.statusCode() shouldBe 404
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "DELETE should return 404 for non-existent data" {
            val response = webClient.delete(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-test/non-existent")
                .send()
                .coAwait()

            response.statusCode() shouldBe 404
        }
    }

    "Tag Management API" - {
        "POST should add tags to a data entry" {
            // Upload a file
            val tempFile = Files.createTempFile("test-tags-", ".txt")
            Files.write(tempFile, "Tag this".toByteArray())

            try {
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/tag-test/taggable")
                    .sendMultipartForm(form)
                    .coAwait()

                // Add tags
                val tagsRequest = JsonObject().put("tags", JsonArray().add("tag1").add("tag2"))
                val response = webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/tag-test/taggable/tags")
                    .sendJsonObject(tagsRequest)
                    .coAwait()

                response.statusCode() shouldBe 200
                val tags = response.bodyAsJsonObject().getJsonArray("tags")
                tags.size() shouldBe 2
                tags.getString(0) shouldBe "tag1"
                tags.getString(1) shouldBe "tag2"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "GET should retrieve tags for a data entry" {
            // Upload a file with tags
            val tempFile = Files.createTempFile("test-get-tags-", ".txt")
            Files.write(tempFile, "Get tags".toByteArray())

            try {
                val metadata = JsonObject().put("tags", JsonArray().add("initial-tag"))
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")
                    .attribute("metadata", metadata.encode())

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/get-tags/file1")
                    .sendMultipartForm(form)
                    .coAwait()

                // Get tags
                val response = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/get-tags/file1/tags")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 200
                val tags = response.bodyAsJsonObject().getJsonArray("tags")
                tags.size() shouldBe 1
                tags.getString(0) shouldBe "initial-tag"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "DELETE should remove a specific tag" {
            // Upload a file with tags
            val tempFile = Files.createTempFile("test-delete-tag-", ".txt")
            Files.write(tempFile, "Delete tag".toByteArray())

            try {
                val metadata = JsonObject().put("tags", JsonArray().add("tag1").add("tag2"))
                val form = MultipartForm.create()
                    .textFileUpload("file", tempFile.fileName.toString(), tempFile.toString(), "text/plain")
                    .attribute("metadata", metadata.encode())

                webClient.post(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-tag-test/file1")
                    .sendMultipartForm(form)
                    .coAwait()

                // Delete tag1
                val response = webClient.delete(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-tag-test/file1/tags/tag1")
                    .send()
                    .coAwait()

                response.statusCode() shouldBe 204

                // Verify only tag2 remains
                val getResponse = webClient.get(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-tag-test/file1/tags")
                    .send()
                    .coAwait()

                val tags = getResponse.bodyAsJsonObject().getJsonArray("tags")
                tags.size() shouldBe 1
                tags.getString(0) shouldBe "tag2"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        "DELETE tag should return 404 for non-existent tag" {
            val response = webClient.delete(port, "localhost", "/api/v1/namespaces/test-data-ns/branches/main/data/delete-tag-test/file1/tags/non-existent")
                .send()
                .coAwait()

            response.statusCode() shouldBe 404
        }
    }
})
