package app.security

import app.config.UploadConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RateLimiterTest {
    private fun config(): UploadConfiguration =
        UploadConfiguration().apply {
            rateLimitEnabled = true
            maxExpiryHours = 1
            maxActiveUploadsPerIp = 1
            initRequestsPerMinute = 1000
            uploadChunkRequestsPerMinute = 1
        }

    @Test
    fun checkUploadChunk_limitsAndReturnsRetryAfter() {
        val limiter = RateLimiter(config())
        val ip = "127.0.0.1"

        val first = limiter.checkUploadChunk(ip)
        assertTrue(first.allowed)

        val second = limiter.checkUploadChunk(ip)
        assertFalse(second.allowed)
        assertTrue(second.retryAfterSeconds in 1..60)
    }

    @Test
    fun checkInit_blocksWhenTooManyActiveUploads() {
        val limiter = RateLimiter(config())
        val ip = "127.0.0.1"

        limiter.registerActiveUpload(ip, "upload-1")
        val result = limiter.checkInit(ip)
        assertFalse(result.allowed)
        assertTrue(result.retryAfterSeconds >= 1)
    }

    @Test
    fun unregisterActiveUpload_removesFromAllIps() {
        val limiter = RateLimiter(config())
        limiter.registerActiveUpload("127.0.0.1", "same-upload")
        limiter.registerActiveUpload("192.0.2.1", "same-upload")

        limiter.unregisterActiveUpload("same-upload")

        val a = limiter.checkInit("127.0.0.1")
        val b = limiter.checkInit("192.0.2.1")
        assertEquals(true, a.allowed)
        assertEquals(true, b.allowed)
    }
}
