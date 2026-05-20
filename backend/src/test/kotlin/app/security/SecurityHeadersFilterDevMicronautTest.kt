package app.security

import app.TestStorageDirs
import app.api.InitUploadRequest
import app.api.InitUploadResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityHeadersFilterDevMicronautTest : TestPropertyProvider {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private val storageDir: Path = TestStorageDirs.createTempStorageDir("secure-upload-devheaders-it")

    override fun getProperties(): MutableMap<String, String> = mutableMapOf(
        "secure-upload.storage-dir" to storageDir.toString(),
        "secure-upload.rate-limit-enabled" to "false",
        "secure-upload.cleanup-enabled" to "false"
    )

    @AfterAll
    fun cleanup() {
        TestStorageDirs.deleteRecursively(storageDir)
    }

    @Test
    fun devEnvironment_doesNotAddStrictSecurityHeaders() {
        val resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/uploads/init", InitUploadRequest(chunkSize = 8_192)),
            InitUploadResponse::class.java
        )
        assertEquals(HttpStatus.OK, resp.status)
        val headers = resp.headers
        // in dev these should not be set by our filter
        assertTrue(headers.get("Strict-Transport-Security") == null)
        assertTrue(headers.get("Content-Security-Policy") == null)
    }
}

