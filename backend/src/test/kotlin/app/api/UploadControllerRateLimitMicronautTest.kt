package app.api

import app.TestStorageDirs
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
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
class UploadControllerRateLimitMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-file-upload-rl-it")

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-file-upload.storage-dir" to storageDir.toString(),
        "secure-file-upload.cleanup-enabled" to "false",
        "secure-file-upload.rate-limit-enabled" to "true",
        "secure-file-upload.max-active-uploads-per-ip" to "999",
        "secure-file-upload.init-requests-per-minute" to "1",
        "secure-file-upload.upload-chunk-requests-per-minute" to "9999",
        "secure-file-upload.download-requests-per-minute" to "9999",
        "secure-file-upload.download-chunk-requests-per-minute" to "9999"
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun init_twice_returns429WithRetryAfter() {
        val req = HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192))
        val first = client.toBlocking().exchange(req, InitUploadResponse::class.java)
        assertEquals(HttpStatus.OK, first.status)

        val ex = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException::class.java) {
            client.toBlocking().exchange(req, String::class.java)
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status)
        val retryAfter = ex.response.headers.get("Retry-After")
        assertNotNull(retryAfter)
        assertTrue(retryAfter!!.toLong() in 1..60)
    }
}

