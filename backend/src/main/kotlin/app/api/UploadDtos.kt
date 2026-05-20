package app.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Introspected
@Serdeable
data class InitUploadRequest(
    val encryptedManifestSize: Long? = null,
    val expectedEncryptedSize: Long? = null,
    @field:Min(1)
    @field:Max(67108864) // hard-cap: 64MiB (matches default backend config max-chunk-bytes)
    val chunkSize: Long? = null
)

@Introspected
@Serdeable
data class InitUploadResponse(
    val uploadId: String,
    val chunkSize: Long,
    val protocolVersion: String,
    val uploadUrl: String,
    val completeUrl: String
)

@Introspected
@Serdeable
data class CompleteUploadRequest(
    @field:Size(max = 5_000_000) // safety guard: base64url-ish JSON, keep bounded
    val encryptedManifest: String,
    @field:Min(1)
    val chunkCount: Int,
    @field:Min(1)
    val encryptedSize: Long,
    @field:Min(1)
    @field:Max(67108864)
    val chunkSize: Long,
    @field:Pattern(regexp = "v\\d+")
    val protocolVersion: String
)

@Introspected
@Serdeable
data class CompleteUploadResponse(
    val uploadId: String,
    val downloadPath: String
)

@Introspected
@Serdeable
data class UploadStatusResponse(
    val uploadId: String,
    val completed: Boolean,
    val uploadedChunks: List<Int>,
    val protocolVersion: String,
    val chunkSize: Long,
    val expiresAt: String?,
    val encryptedSize: Long? = null,
    val chunkCount: Int? = null
)
