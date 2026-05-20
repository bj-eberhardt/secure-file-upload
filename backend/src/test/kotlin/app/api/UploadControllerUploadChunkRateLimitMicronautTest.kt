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
import java.nio.file.Path

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadControllerUploadChunkRateLimitMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-upload-uplrl-it")

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-upload.storage-dir" to storageDir.toString(),
        "secure-upload.cleanup-enabled" to "false",
        "secure-upload.rate-limit-enabled" to "true",
        "secure-upload.upload-chunk-requests-per-minute" to "1",
        "secure-upload.init-requests-per-minute" to "9999",
        "secure-upload.download-requests-per-minute" to "9999",
        "secure-upload.download-chunk-requests-per-minute" to "9999",
        "secure-upload.max-active-uploads-per-ip" to "999"
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun uploadChunk_secondRequestIs429_withRetryAfter() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192)),
            InitUploadResponse::class.java
        )
        val id = init.uploadId
        val bytes = byteArrayOf(1, 2, 3)

        val put = HttpRequest.PUT("/api/v1/uploads/$id/chunks/0", bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        val first = client.toBlocking().exchange(put, Any::class.java)
        assertEquals(HttpStatus.NO_CONTENT, first.status)

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(put, String::class.java)
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
        val retryAfter = ex.response.headers.get("Retry-After")
        assertNotNull(retryAfter)
        assertTrue(retryAfter!!.toLong() in 1..60)
    }
}

