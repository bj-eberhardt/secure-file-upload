package app.config

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("secure-file-upload")
class UploadConfiguration {
    var storageDir: String = "storage/uploads"
    var chunkSize: Long = 8L * 1024L * 1024L
    var minChunkBytes: Long = 1L * 1024L * 1024L
    var defaultExpiryHours: Long = 72
    var maxExpiryHours: Long = 168
    var protocolVersion: String = "v1"
    // Max request body size for a packed chunk (header+nonce+ciphertext), not plaintext chunk size.
    var maxChunkBytes: Long = 64L * 1024L * 1024L
    var trustProxyHeaders: Boolean = false

    // "Maximal" hardening defaults (in-memory, resets on restart)
    var rateLimitEnabled: Boolean = true
    var maxActiveUploadsPerIp: Int = 5
    var initRequestsPerMinute: Int = 30
    var downloadRequestsPerMinute: Int = 60
    var downloadChunkRequestsPerMinute: Int = 600
    var uploadChunkRequestsPerMinute: Int = 600

    var cleanupEnabled: Boolean = true
    var cleanupIntervalMinutes: Long = 15
}
