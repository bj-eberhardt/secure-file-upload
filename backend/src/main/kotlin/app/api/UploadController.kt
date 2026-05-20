package app.api

import app.config.UploadConfiguration
import app.storage.UploadStorage
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import java.nio.file.Files

@Controller("/api")
class UploadController(
    private val storage: UploadStorage,
    private val config: UploadConfiguration
) {
    @Post("/uploads/init")
    fun init(@Body request: InitUploadRequest): InitUploadResponse {
        val id = storage.createUpload()
        return InitUploadResponse(
            uploadId = id,
            chunkSize = request.chunkSize ?: config.chunkSize,
            uploadUrl = "/api/uploads/$id/chunks/{index}",
            completeUrl = "/api/uploads/$id/complete"
        )
    }

    @Put("/uploads/{id}/chunks/{index}", consumes = [MediaType.APPLICATION_OCTET_STREAM])
    fun uploadChunk(id: String, index: Int, @Body body: ByteArray): HttpResponse<Any> {
        storage.writeChunk(id, index, body)
        return HttpResponse.noContent()
    }

    @Post("/uploads/{id}/complete")
    fun complete(id: String, @Body request: CompleteUploadRequest): CompleteUploadResponse {
        storage.complete(id, request.encryptedManifest, request.chunkCount, request.encryptedSize)
        return CompleteUploadResponse(id, "/d/$id")
    }

    @Get("/uploads/{id}/status")
    fun status(id: String): UploadStatusResponse = UploadStatusResponse(
        uploadId = id,
        completed = storage.isCompleted(id),
        uploadedChunks = storage.uploadedChunks(id)
    )

    @Get("/downloads/{id}", produces = [MediaType.APPLICATION_OCTET_STREAM])
    fun download(id: String): HttpResponse<ByteArray> {
        val path = storage.blobPath(id)
        if (!Files.exists(path)) return HttpResponse.notFound()
        return HttpResponse.ok(Files.readAllBytes(path))
            .header("Content-Disposition", "attachment; filename=encrypted-upload.bin")
    }
}
