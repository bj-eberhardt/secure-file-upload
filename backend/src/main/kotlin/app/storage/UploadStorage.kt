package app.storage

import app.api.UnknownUploadException
import app.api.UploadConflictException
import app.api.UploadExpiredException
import app.config.UploadConfiguration
import tools.jackson.databind.ObjectMapper
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Singleton
class UploadStorage(
    private val config: UploadConfiguration,
    private val objectMapper: ObjectMapper
) {
    private val random = SecureRandom()
    private val baseDir: Path = Path.of(config.storageDir)
    private val packOverheadBytes = 8L + 12L + 16L // v1 packed chunk overhead: header + nonce + GCM tag

    init {
        Files.createDirectories(baseDir)
        require(config.maxChunkBytes > packOverheadBytes) { "secure-upload.max-chunk-bytes must be > packed overhead ($packOverheadBytes)" }
        require(config.minChunkBytes >= 1) { "secure-upload.min-chunk-bytes must be >= 1" }
        require(config.minChunkBytes <= (config.maxChunkBytes - packOverheadBytes)) {
            "secure-upload.min-chunk-bytes must be <= (max-chunk-bytes - overhead)"
        }
    }

    fun createUpload(chunkSize: Long, protocolVersion: String): String {
        require(protocolVersion.matches(Regex("v\\d+"))) { "Invalid protocol version" }
        require(chunkSize >= config.minChunkBytes) { "Chunk size must be >= minChunkBytes" }
        require(chunkSize <= (config.maxChunkBytes - packOverheadBytes)) { "Chunk size exceeds max (plain)" }
        val id = newId()
        Files.createDirectories(uploadDir(id).resolve("chunks"))
        val createdAt = Instant.now()
        val defaultExpiry = createdAt.plus(config.defaultExpiryHours, ChronoUnit.HOURS)
        val maxExpiry = createdAt.plus(config.maxExpiryHours, ChronoUnit.HOURS)
        val expiresAt = if (defaultExpiry.isAfter(maxExpiry)) maxExpiry else defaultExpiry
        writeMeta(
            id,
            UploadMeta(
                id = id,
                protocolVersion = protocolVersion,
                chunkSize = chunkSize,
                createdAt = createdAt.toString(),
                expiresAt = expiresAt.toString(),
                completed = false
            )
        )
        return id
    }

    fun writeChunk(uploadId: String, index: Int, bytes: ByteArray) {
        require(index >= 0) { "Chunk index must be >= 0" }
        require(bytes.size.toLong() <= config.maxChunkBytes) { "Chunk too large" } // packed bytes size
        val meta = readMetaOrNull(uploadId) ?: throw UnknownUploadException()
        if (meta.completed) throw UploadConflictException("Upload already completed")
        if (isExpired(uploadId)) throw UploadExpiredException()

        val dir = uploadDir(uploadId).resolve("chunks")
        if (!dir.exists()) throw UnknownUploadException()

        val baseName = index.toString().padStart(8, '0')
        val partPath = dir.resolve("$baseName.part")
        val hashPath = dir.resolve("$baseName.sha256")
        val incomingHash = sha256Hex(bytes)

        if (Files.exists(partPath)) {
            val existingSize = Files.size(partPath)
            if (existingSize != bytes.size.toLong()) {
                throw UploadConflictException("Chunk $index already exists with different size")
            }
            val existingHash = ensureChunkHash(partPath, hashPath)
            if (!existingHash.equals(incomingHash, ignoreCase = true)) {
                throw UploadConflictException("Chunk $index already exists with different content")
            }
            // idempotent re-upload: same size + same hash -> accept
            return
        }

        val tmpPart = dir.resolve("$baseName.part.tmp")
        val tmpHash = dir.resolve("$baseName.sha256.tmp")
        Files.write(tmpPart, bytes)
        atomicMove(tmpPart, partPath)
        Files.writeString(tmpHash, "$incomingHash\n")
        atomicMove(tmpHash, hashPath)
    }

    fun complete(
        uploadId: String,
        encryptedManifest: String,
        chunkCount: Int,
        encryptedSize: Long,
        chunkSize: Long,
        protocolVersion: String
    ) {
        val dir = uploadDir(uploadId)
        if (!dir.exists()) throw UnknownUploadException()
        if (isExpired(uploadId)) throw UploadExpiredException()
        if (chunkSize < config.minChunkBytes || chunkSize > (config.maxChunkBytes - packOverheadBytes)) {
            throw IllegalArgumentException("Invalid chunk size")
        }
        if (!protocolVersion.matches(Regex("v\\d+"))) throw IllegalArgumentException("Invalid protocol version")

        val chunks = dir.resolve("chunks").listDirectoryEntries("*.part").sorted()
        if (chunks.size != chunkCount) throw UploadConflictException("Expected $chunkCount chunks but found ${chunks.size}")
        val indices = chunks.map { path ->
            path.fileName.toString().substringBefore('.').toIntOrNull()
                ?: throw UploadConflictException("Invalid chunk filename: ${path.fileName}")
        }
        val expected = (0 until chunkCount).toList()
        if (indices != expected) {
            throw UploadConflictException("Chunk indices are inconsistent (expected 0..${chunkCount - 1})")
        }

        // verify chunk hashes exist (and are correct for the stored bytes)
        chunks.forEachIndexed { idx, chunkPath ->
            val hashPath = chunkPath.parent.resolve(idx.toString().padStart(8, '0') + ".sha256")
            val actualHash = sha256Hex(chunkPath)
            if (hashPath.exists()) {
                val storedHash = hashPath.readText().trim()
                if (!storedHash.equals(actualHash, ignoreCase = true)) {
                    throw UploadConflictException("Chunk $idx hash mismatch")
                }
            } else {
                val tmp = chunkPath.parent.resolve(hashPath.fileName.toString() + ".tmp")
                Files.writeString(tmp, "$actualHash\n")
                atomicMove(tmp, hashPath)
            }
        }

        val totalBytes = chunks.sumOf { Files.size(it) }
        if (totalBytes != encryptedSize) throw UploadConflictException("encryptedSize mismatch (expected $encryptedSize, got $totalBytes)")

        Files.writeString(dir.resolve("manifest.enc.b64"), encryptedManifest)

        val previous = readMetaOrNull(uploadId)
        val completedAt = Instant.now().toString()
        val createdAt = previous?.createdAt ?: Instant.now().toString()
        val expiresAt = previous?.expiresAt
        writeMeta(
            uploadId,
            UploadMeta(
                id = uploadId,
                protocolVersion = protocolVersion,
                chunkSize = chunkSize,
                createdAt = createdAt,
                expiresAt = expiresAt,
                completed = true,
                encryptedSize = encryptedSize,
                chunkCount = chunkCount,
                completedAt = completedAt
            )
        )
    }

    fun metaPath(uploadId: String): Path = uploadDir(uploadId).resolve("upload.json")
    fun manifestPath(uploadId: String): Path = uploadDir(uploadId).resolve("manifest.enc.b64")
    fun chunkPath(uploadId: String, index: Int): Path =
        uploadDir(uploadId).resolve("chunks").resolve(index.toString().padStart(8, '0') + ".part")

    fun uploadedChunks(uploadId: String): List<Int> {
        val dir = uploadDir(uploadId).resolve("chunks")
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries("*.part")
            .mapNotNull { it.fileName.toString().substringBefore('.').toIntOrNull() }
            .sorted()
    }

    fun isCompleted(uploadId: String): Boolean {
        val meta = readMetaOrNull(uploadId) ?: return false
        return meta.completed && manifestPath(uploadId).exists()
    }

    fun readMetaOrNull(uploadId: String): UploadMeta? {
        val path = metaPath(uploadId)
        if (!path.exists()) return null
        return objectMapper.readValue(path.readText(), UploadMeta::class.java)
    }

    fun readEncryptedManifestOrNull(uploadId: String): String? {
        val path = manifestPath(uploadId)
        if (!path.exists()) return null
        return path.readText()
    }

    fun isExpired(uploadId: String): Boolean {
        val meta = readMetaOrNull(uploadId) ?: return false
        val expiresAt = meta.expiresAt ?: return false
        return Instant.now().isAfter(Instant.parse(expiresAt))
    }

    fun touchExpiry(uploadId: String) {
        val meta = readMetaOrNull(uploadId) ?: return
        if (meta.expiresAt == null) return
        if (!meta.completed) return

        val createdAt = Instant.parse(meta.createdAt)
        val currentExpiresAt = Instant.parse(meta.expiresAt)
        val candidate = Instant.now().plus(config.defaultExpiryHours, ChronoUnit.HOURS)
        val cap = createdAt.plus(config.maxExpiryHours, ChronoUnit.HOURS)
        val clamped = if (candidate.isAfter(cap)) cap else candidate
        val extended = if (currentExpiresAt.isAfter(clamped)) currentExpiresAt else clamped
        val newExpiresAt = if (extended.isAfter(cap)) cap else extended
        if (newExpiresAt != currentExpiresAt) {
            writeMeta(uploadId, meta.copy(expiresAt = newExpiresAt.toString()))
        }
    }

    private fun writeMeta(uploadId: String, meta: UploadMeta) {
        val path = metaPath(uploadId)
        path.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta))
    }

    private fun uploadDir(id: String): Path {
        require(id.matches(Regex("[A-Za-z0-9_-]{22,64}"))) { "Invalid upload id" }
        return baseDir.resolve(id)
    }

    private fun newId(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureChunkHash(partPath: Path, hashPath: Path): String {
        if (hashPath.exists()) {
            return hashPath.readText().trim()
        }
        val computed = sha256Hex(partPath)
        val tmp = hashPath.parent.resolve(hashPath.fileName.toString() + ".tmp")
        Files.writeString(tmp, "$computed\n")
        atomicMove(tmp, hashPath)
        return computed
    }

    private fun atomicMove(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

data class UploadMeta(
    val id: String,
    val protocolVersion: String,
    val chunkSize: Long,
    val createdAt: String,
    val expiresAt: String? = null,
    val completed: Boolean,
    val encryptedSize: Long? = null,
    val chunkCount: Int? = null,
    val completedAt: String? = null
)
