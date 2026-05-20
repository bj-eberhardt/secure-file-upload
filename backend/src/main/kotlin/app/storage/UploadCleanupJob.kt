package app.storage

import app.config.UploadConfiguration
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant

@Singleton
@Requires(property = "secure-upload.cleanup-enabled", value = "true", defaultValue = "true")
class UploadCleanupJob(
    private val config: UploadConfiguration,
    private val storage: UploadStorage
) {
    private val idRegex = Regex("[A-Za-z0-9_-]{22,64}")

    @Scheduled(fixedDelay = "\${secure-upload.cleanup-interval-minutes:15}m")
    fun cleanupExpiredUploads() {
        val base = Path.of(config.storageDir)
        if (!Files.exists(base) || !Files.isDirectory(base)) return

        Files.newDirectoryStream(base).use { stream ->
            for (entry in stream) {
                if (!Files.isDirectory(entry)) continue
                val id = entry.fileName.toString()
                if (!idRegex.matches(id)) continue

                val meta = storage.readMetaOrNull(id) ?: continue
                val expiresAt = meta.expiresAt ?: continue
                val expired = try {
                    Instant.now().isAfter(Instant.parse(expiresAt))
                } catch (_: Exception) {
                    false
                }
                if (!expired) continue

                deleteRecursively(entry)
            }
        }
    }

    private fun deleteRecursively(dir: Path) {
        if (!dir.normalize().startsWith(Path.of(config.storageDir).normalize())) return
        Files.walkFileTree(
            dir,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.deleteIfExists(file)
                    } catch (_: Exception) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    try {
                        Files.deleteIfExists(dir)
                    } catch (_: Exception) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )
    }
}

