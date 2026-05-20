package app.api

import app.config.UploadConfiguration
import app.security.ClientIpResolver
import app.security.RateLimiter
import app.storage.UploadStorage
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.validation.Validated
import jakarta.validation.Valid
import java.nio.file.Files
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Controller("/api/v1")
@Validated
@Tag(name = "uploads-v1", description = "Upload API (version v1)")
class UploadController(
    private val storage: UploadStorage,
    private val config: UploadConfiguration,
    private val ipResolver: ClientIpResolver,
    private val rateLimiter: RateLimiter
) {
    private val packOverheadBytes = 8L + 12L + 16L // v1 packed chunk overhead: header + nonce + GCM tag

    @Post("/uploads/init")
    @Operation(
        summary = "Initiate upload",
        description = "Create a new upload session and return the upload id, chunk size and URLs.",
        tags = ["uploads-v1"]
    )
    fun init(request: HttpRequest<*>, @Body @Valid body: InitUploadRequest): HttpResponse<InitUploadResponse> {
        val ip = ipResolver.resolve(request)
        val initLimit = rateLimiter.checkInit(ip)
        if (!initLimit.allowed) {
            return HttpResponse.status<InitUploadResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", initLimit.retryAfterSeconds.toString())
        }
        val requestedChunkSize = body.chunkSize ?: config.chunkSize
        val maxPlainChunkSize = (config.maxChunkBytes - packOverheadBytes).coerceAtLeast(1)
        val chunkSize = requestedChunkSize
            .coerceAtMost(maxPlainChunkSize)
            .coerceAtLeast(config.minChunkBytes.coerceAtLeast(1))
        val id = storage.createUpload(chunkSize = chunkSize, protocolVersion = config.protocolVersion)
        rateLimiter.registerActiveUpload(ip, id)
        return HttpResponse.ok(
            InitUploadResponse(
                uploadId = id,
                chunkSize = chunkSize,
                protocolVersion = config.protocolVersion,
                uploadUrl = "/api/v1/uploads/$id/chunks/{index}",
                completeUrl = "/api/v1/uploads/$id/complete"
            )
        )
    }

    @Put("/uploads/{id}/chunks/{index}", consumes = [MediaType.APPLICATION_OCTET_STREAM])
    @Operation(
        summary = "Upload chunk",
        description = "Upload a single chunk (binary) for the given upload id and index.",
        tags = ["uploads-v1"],
        responses = [ApiResponse(responseCode = "204", description = "Chunk stored"), ApiResponse(responseCode = "429", description = "Too many requests")]
    )
    fun uploadChunk(
        request: HttpRequest<*>,
        @Parameter(description = "Upload id") id: String,
        @Parameter(description = "Chunk index (0-based)") index: Int,
        @Body body: ByteArray
    ): HttpResponse<Any> {
        val ip = ipResolver.resolve(request)
        val limit = rateLimiter.checkUploadChunk(ip)
        if (!limit.allowed) {
            return HttpResponse.status<Any>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", limit.retryAfterSeconds.toString())
        }
        val meta = storage.readMetaOrNull(id) ?: return HttpResponse.notFound()
        if (storage.isExpired(id)) return HttpResponse.status(HttpStatus.GONE)
        if (meta.completed) throw UploadConflictException("Upload already completed")
        storage.writeChunk(id, index, body)
        return HttpResponse.noContent()
    }

    @Get("/uploads/{id}/chunks/{index}", produces = [MediaType.APPLICATION_OCTET_STREAM])
    @Operation(
        summary = "Download chunk",
        description = "Download one chunk for an upload as binary stream.",
        tags = ["uploads-v1"],
        responses = [ApiResponse(responseCode = "200", description = "Chunk returned"), ApiResponse(responseCode = "404", description = "Chunk not found"), ApiResponse(responseCode = "429", description = "Too many requests")]
    )
    fun downloadChunk(
        request: HttpRequest<*>,
        @Parameter(description = "Upload id") id: String,
        @Parameter(description = "Chunk index (0-based)") index: Int
    ): HttpResponse<StreamedFile> {
        val ip = ipResolver.resolve(request)
        val limit = rateLimiter.checkDownloadChunk(ip)
        if (!limit.allowed) {
            return HttpResponse.status<StreamedFile>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", limit.retryAfterSeconds.toString())
        }
        if (storage.isExpired(id)) return HttpResponse.status(HttpStatus.GONE)
        val meta = storage.readMetaOrNull(id) ?: return HttpResponse.notFound()
        if (!meta.completed) return HttpResponse.notFound()
        val path = storage.chunkPath(id, index)
        if (!Files.exists(path)) return HttpResponse.notFound()
        storage.touchExpiry(id)
        val streamed = StreamedFile(Files.newInputStream(path), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        return HttpResponse.ok(streamed)
    }

    @Post("/uploads/{id}/complete")
    @Operation(
        summary = "Complete upload",
        description = "Complete an upload by providing manifest and metadata; finalizes the stored upload.",
        tags = ["uploads-v1"],
        responses = [ApiResponse(responseCode = "200", description = "Upload completed"), ApiResponse(responseCode = "409", description = "Conflict / protocol mismatch"), ApiResponse(responseCode = "429", description = "Too many requests")]
    )
    fun complete(
        request: HttpRequest<*>,
        @Parameter(description = "Upload id") id: String,
        @Body @Valid body: CompleteUploadRequest
    ): HttpResponse<CompleteUploadResponse> {
        val ip = ipResolver.resolve(request)
        val limit = rateLimiter.checkUploadChunk(ip)
        if (!limit.allowed) {
            return HttpResponse.status<CompleteUploadResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", limit.retryAfterSeconds.toString())
        }
        val meta = storage.readMetaOrNull(id) ?: throw UnknownUploadException()
        if (storage.isExpired(id)) throw UploadExpiredException()
        if (meta.protocolVersion != body.protocolVersion) throw UploadConflictException("protocolVersion mismatch")
        if (meta.chunkSize != body.chunkSize) throw UploadConflictException("chunkSize mismatch")

        storage.complete(
            uploadId = id,
            encryptedManifest = body.encryptedManifest,
            chunkCount = body.chunkCount,
            encryptedSize = body.encryptedSize,
            chunkSize = body.chunkSize,
            protocolVersion = body.protocolVersion
        )
        rateLimiter.unregisterActiveUpload(id)
        return HttpResponse.ok(CompleteUploadResponse(id, "/d/$id"))
    }

    @Get("/uploads/{id}/status")
    @Operation(
        summary = "Upload status",
        description = "Get current status and metadata for an upload.",
        tags = ["uploads-v1"],
        responses = [ApiResponse(responseCode = "200", description = "Status returned"), ApiResponse(responseCode = "404", description = "Not found"), ApiResponse(responseCode = "429", description = "Too many requests")]
    )
    fun status(request: HttpRequest<*>, @Parameter(description = "Upload id") id: String): HttpResponse<UploadStatusResponse> {
        val ip = ipResolver.resolve(request)
        val limit = rateLimiter.checkDownload(ip)
        if (!limit.allowed) {
            return HttpResponse.status<UploadStatusResponse>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", limit.retryAfterSeconds.toString())
        }
        val meta = storage.readMetaOrNull(id) ?: return HttpResponse.notFound()
        if (storage.isExpired(id)) return HttpResponse.status(HttpStatus.GONE)
        val response = UploadStatusResponse(
            uploadId = id,
            completed = storage.isCompleted(id),
            uploadedChunks = storage.uploadedChunks(id),
            protocolVersion = meta.protocolVersion,
            chunkSize = meta.chunkSize,
            expiresAt = meta.expiresAt,
            encryptedSize = meta.encryptedSize,
            chunkCount = meta.chunkCount
        )
        return HttpResponse.ok(response)
    }

    @Get("/uploads/{id}/manifest")
    @Operation(
        summary = "Get manifest",
        description = "Retrieve the encrypted manifest for an upload.",
        tags = ["uploads-v1"],
        responses = [ApiResponse(responseCode = "200", description = "Manifest returned"), ApiResponse(responseCode = "404", description = "Not found"), ApiResponse(responseCode = "429", description = "Too many requests")]
    )
    fun manifest(request: HttpRequest<*>, @Parameter(description = "Upload id") id: String): HttpResponse<String> {
        val ip = ipResolver.resolve(request)
        val limit = rateLimiter.checkDownload(ip)
        if (!limit.allowed) {
            return HttpResponse.status<String>(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", limit.retryAfterSeconds.toString())
        }
        if (storage.isExpired(id)) return HttpResponse.status(HttpStatus.GONE)
        val manifest = storage.readEncryptedManifestOrNull(id) ?: return HttpResponse.notFound()
        return HttpResponse.ok(manifest).contentType(MediaType.TEXT_PLAIN_TYPE)
    }
}
