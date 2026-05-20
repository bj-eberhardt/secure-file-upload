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
class UploadControllerManifestRateLimitMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-upload-manifestrl-it")

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-upload.storage-dir" to storageDir.toString(),
        "secure-upload.cleanup-enabled" to "false",
        "secure-upload.rate-limit-enabled" to "true",
        "secure-upload.download-requests-per-minute" to "1",
        "secure-upload.init-requests-per-minute" to "9999",
        "secure-upload.upload-chunk-requests-per-minute" to "9999",
        "secure-upload.download-chunk-requests-per-minute" to "9999",
        "secure-upload.max-active-uploads-per-ip" to "999"
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun manifest_secondRequestIs429_withRetryAfter() {
        val init = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192)),
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
                    encryptedManifest = "manifest",
                    chunkCount = 1,
                    encryptedSize = bytes.size.toLong(),
                    chunkSize = init.chunkSize,
                    protocolVersion = init.protocolVersion
                )
            ),
            Any::class.java
        )

        val first = client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/manifest"), String::class.java)
        assertEquals(HttpStatus.OK, first.status)
        assertEquals("manifest", first.body())

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(HttpRequest.GET<Any>("/api/v1/uploads/$id/manifest"), String::class.java)
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
        val retryAfter = ex.response.headers.get("Retry-After")
        assertNotNull(retryAfter)
        assertTrue(retryAfter!!.toLong() in 1..60)
    }
}

