package app.config

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("secure-upload")
class UploadConfiguration {
    var storageDir: String = "storage/uploads"
    var chunkSize: Long = 8L * 1024L * 1024L
    var defaultExpiryHours: Long = 72
}
