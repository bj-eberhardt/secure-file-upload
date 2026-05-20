package app.api

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class InitUploadRequest(
    val encryptedManifestSize: Long? = null,
    val expectedEncryptedSize: Long? = null,
    val chunkSize: Long? = null
)

@Serdeable
data class InitUploadResponse(
    val uploadId: String,
    val chunkSize: Long,
    val uploadUrl: String,
    val completeUrl: String
)

@Serdeable
data class CompleteUploadRequest(
    val encryptedManifest: String? = null,
    val chunkCount: Int,
    val encryptedSize: Long
)

@Serdeable
data class CompleteUploadResponse(
    val uploadId: String,
    val downloadPath: String
)

@Serdeable
data class UploadStatusResponse(
    val uploadId: String,
    val completed: Boolean,
    val uploadedChunks: List<Int>
)
