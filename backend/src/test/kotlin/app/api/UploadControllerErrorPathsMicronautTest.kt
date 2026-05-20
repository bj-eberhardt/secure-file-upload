package app.api

import app.TestStorageDirs
import app.storage.UploadStorage
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadControllerErrorPathsMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var storage: UploadStorage

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-upload-errors-it")
    private val mapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-upload.storage-dir" to storageDir.toString(),
        "secure-upload.rate-limit-enabled" to "false",
        "secure-upload.cleanup-enabled" to "false",
        "secure-upload.max-chunk-bytes" to "64",
        "secure-upload.min-chunk-bytes" to "4",
        "secure-upload.chunk-size" to "8"
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun init_clampsToMinChunkBytes() {
        val resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 1)),
            InitUploadResponse::class.java
        )
        assertEquals(HttpStatus.OK, resp.status)
        assertEquals(4L, resp.body()!!.chunkSize)
    }

    @Test
    fun uploadChunk_unknownId_returns404() {
        val put = HttpRequest.PUT("/api/v1/uploads/AAAAAAAAAAAAAAAAAAAAAA/chunks/0", byteArrayOf(1))
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(put, String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun uploadChunk_tooLarge_returns400() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val bytes = ByteArray(65) { 1 } // > max-chunk-bytes (64)
        val put = HttpRequest.PUT("/api/v1/uploads/${init.uploadId}/chunks/0", bytes)
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(put, String::class.java)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun complete_unknownId_returns404() {
        val req = CompleteUploadRequest(
            encryptedManifest = "m",
            chunkCount = 1,
            encryptedSize = 1,
            chunkSize = 8,
            protocolVersion = "v1"
        )
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/uploads/AAAAAAAAAAAAAAAAAAAAAA/complete", req), String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun complete_expired_returns410() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val meta = storage.readMetaOrNull(id)!!
        val expired = meta.copy(expiresAt = Instant.EPOCH.toString())
        Files.writeString(storage.metaPath(id), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expired))

        val req = CompleteUploadRequest(
            encryptedManifest = "m",
            chunkCount = 1,
            encryptedSize = 1,
            chunkSize = 8,
            protocolVersion = "v1"
        )
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/uploads/$id/complete", req), String::class.java)
        }
        assertEquals(HttpStatus.GONE, ex.status)
    }

    @Test
    fun manifest_unknownId_returns404() {
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/AAAAAAAAAAAAAAAAAAAAAA/manifest"), String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun status_expired_returns410() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val meta = storage.readMetaOrNull(id)!!
        val expired = meta.copy(expiresAt = Instant.EPOCH.toString())
        Files.writeString(storage.metaPath(id), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expired))

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/status"), String::class.java)
        }
        assertEquals(HttpStatus.GONE, ex.status)
    }

    @Test
    fun downloadChunk_missingIndex_returns404_afterComplete() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val bytes = byteArrayOf(1, 2, 3)
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/uploads/$id/chunks/0", bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
            Any::class.java
        )
        client.toBlocking().exchange(
            HttpRequest.POST(
                "/api/v1/uploads/$id/complete",
                CompleteUploadRequest(
                    encryptedManifest = "m",
                    chunkCount = 1,
                    encryptedSize = bytes.size.toLong(),
                    chunkSize = init.chunkSize,
                    protocolVersion = init.protocolVersion
                )
            ),
            Any::class.java
        )

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/chunks/1"), String::class.java)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun downloadChunk_expired_returns410() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val bytes = byteArrayOf(1, 2, 3)
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/uploads/$id/chunks/0", bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
            Any::class.java
        )
        client.toBlocking().exchange(
            HttpRequest.POST(
                "/api/v1/uploads/$id/complete",
                CompleteUploadRequest(
                    encryptedManifest = "m",
                    chunkCount = 1,
                    encryptedSize = bytes.size.toLong(),
                    chunkSize = init.chunkSize,
                    protocolVersion = init.protocolVersion
                )
            ),
            Any::class.java
        )

        val meta = storage.readMetaOrNull(id)!!
        val expired = meta.copy(expiresAt = Instant.EPOCH.toString())
        Files.writeString(storage.metaPath(id), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expired))

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/chunks/0"), String::class.java)
        }
        assertEquals(HttpStatus.GONE, ex.status)
    }

    @Test
    fun manifest_expired_returns410() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val bytes = byteArrayOf(1, 2, 3)
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/uploads/$id/chunks/0", bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
            Any::class.java
        )
        client.toBlocking().exchange(
            HttpRequest.POST(
                "/api/v1/uploads/$id/complete",
                CompleteUploadRequest(
                    encryptedManifest = "m",
                    chunkCount = 1,
                    encryptedSize = bytes.size.toLong(),
                    chunkSize = init.chunkSize,
                    protocolVersion = init.protocolVersion
                )
            ),
            Any::class.java
        )

        val meta = storage.readMetaOrNull(id)!!
        val expired = meta.copy(expiresAt = Instant.EPOCH.toString())
        Files.writeString(storage.metaPath(id), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expired))

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/manifest"), String::class.java)
        }
        assertEquals(HttpStatus.GONE, ex.status)
    }
}
