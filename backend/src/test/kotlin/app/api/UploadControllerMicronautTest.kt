package app.api

import app.TestStorageDirs
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.charset.StandardCharsets
import java.nio.file.Path

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadControllerMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-file-upload-it")

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-file-upload.storage-dir" to storageDir.toString(),
        "secure-file-upload.rate-limit-enabled" to "false",
        "secure-file-upload.cleanup-enabled" to "false",
        "secure-file-upload.max-chunk-bytes" to (64L * 1024L).toString(), // small for tests
        "secure-file-upload.min-chunk-bytes" to (1024L).toString(),
        "secure-file-upload.chunk-size" to (8L * 1024L).toString()
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun init_returnsUrlsAndClampedChunkSize() {
        val resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 67_108_864)),
            InitUploadResponse::class.java
        )
        assertEquals(HttpStatus.OK, resp.status)
        val body = resp.body()
        assertNotNull(body)
        body!!
        assertTrue(body.uploadId.matches(Regex("[A-Za-z0-9_-]{22,64}")))
        assertEquals("v1", body.protocolVersion)
        assertTrue(body.uploadUrl.startsWith("/api/v1/uploads/${body.uploadId}/chunks/"))
        assertTrue(body.completeUrl.startsWith("/api/v1/uploads/${body.uploadId}/complete"))

        // max plain size is maxChunkBytes - 36 overhead
        val expectedMaxPlain = 64L * 1024L - 36L
        assertEquals(expectedMaxPlain, body.chunkSize)
    }

    @Test
    fun status_unknown_returns404() {
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/v1/uploads/AAAAAAAAAAAAAAAAAAAAAA/status"),
                String::class.java
            )
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun uploadChunk_idempotentAndConflict_andDownloadAfterComplete() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192)),
            InitUploadResponse::class.java
        )

        val uploadId = init.uploadId
        val chunkIndex = 0
        val bytes1 = "hello".toByteArray(StandardCharsets.UTF_8)
        val bytes2 = "world".toByteArray(StandardCharsets.UTF_8)

        val put1 = HttpRequest.PUT("/api/v1/uploads/$uploadId/chunks/$chunkIndex", bytes1)
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val resp1 = client.toBlocking().exchange(put1, Any::class.java)
        assertEquals(HttpStatus.NO_CONTENT, resp1.status)

        val put2 = HttpRequest.PUT("/api/v1/uploads/$uploadId/chunks/$chunkIndex", bytes1)
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val resp2 = client.toBlocking().exchange(put2, Any::class.java)
        assertEquals(HttpStatus.NO_CONTENT, resp2.status)

        val putConflict = HttpRequest.PUT("/api/v1/uploads/$uploadId/chunks/$chunkIndex", bytes2)
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val conflictEx = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(putConflict, String::class.java)
        }
        assertEquals(HttpStatus.CONFLICT, conflictEx.status)

        val completeReq = CompleteUploadRequest(
            encryptedManifest = """{"version":"enc-manifest-v1","protocolVersion":"v1","payloadB64u":"AA"}""",
            chunkCount = 1,
            encryptedSize = bytes1.size.toLong(),
            chunkSize = init.chunkSize,
            protocolVersion = init.protocolVersion
        )
        val complete = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/uploads/$uploadId/complete", completeReq),
            CompleteUploadResponse::class.java
        )
        assertEquals(HttpStatus.OK, complete.status)
        assertEquals("/d/$uploadId", complete.body()!!.downloadPath)

        // After complete: PUT must be 409
        val putAfter = HttpRequest.PUT("/api/v1/uploads/$uploadId/chunks/1", "x".toByteArray())
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val afterEx = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(putAfter, String::class.java)
        }
        assertEquals(HttpStatus.CONFLICT, afterEx.status)

        // Before/after semantics for GET chunk: now completed => GET should work for existing chunk
        val get = client.toBlocking().exchange(
            HttpRequest.GET<Any>("/api/v1/uploads/$uploadId/chunks/0"),
            ByteArray::class.java
        )
        assertEquals(HttpStatus.OK, get.status)
        assertEquals(bytes1.toList(), get.body()!!.toList())
    }

    @Test
    fun downloadChunk_beforeComplete_returns404() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192)),
            InitUploadResponse::class.java
        )
        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(
                HttpRequest.GET<Any>("/api/v1/uploads/${init.uploadId}/chunks/0"),
                String::class.java
            )
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }
}
