package app.security

import app.config.UploadConfiguration
import jakarta.inject.Singleton
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class RateLimitResult(
    val allowed: Boolean,
    val retryAfterSeconds: Long = 0
)

@Singleton
class RateLimiter(private val config: UploadConfiguration) {
    private data class Window(var windowStartEpochMinute: Long, var count: Int)

    private val initWindows = ConcurrentHashMap<String, Window>()
    private val uploadWindows = ConcurrentHashMap<String, Window>()
    private val downloadWindows = ConcurrentHashMap<String, Window>()
    private val downloadChunkWindows = ConcurrentHashMap<String, Window>()

    private val activeUploadsByIp = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    fun checkInit(ip: String): RateLimitResult {
        if (!config.rateLimitEnabled) return RateLimitResult(true)
        cleanupActiveUploads(ip)
        val active = activeUploadsByIp[ip]?.size ?: 0
        if (active >= config.maxActiveUploadsPerIp) return RateLimitResult(false, retryAfterSeconds = 60)
        return checkWindow(initWindows, ip, config.initRequestsPerMinute)
    }

    fun registerActiveUpload(ip: String, uploadId: String) {
        activeUploadsByIp.compute(ip) { _, existing ->
            val map = existing ?: ConcurrentHashMap()
            map[uploadId] = Instant.now().epochSecond
            map
        }
    }

    fun unregisterActiveUpload(uploadId: String) {
        activeUploadsByIp.forEach { (_, uploads) -> uploads.remove(uploadId) }
    }

    private fun cleanupActiveUploads(ip: String) {
        val ttlSeconds = ChronoUnit.HOURS.duration.seconds * config.maxExpiryHours
        val cutoff = Instant.now().epochSecond - ttlSeconds
        val uploads = activeUploadsByIp[ip] ?: return
        uploads.entries.removeIf { (_, startedAt) -> startedAt < cutoff }
        if (uploads.isEmpty()) activeUploadsByIp.remove(ip, uploads)
    }

    fun checkUploadChunk(ip: String): RateLimitResult {
        if (!config.rateLimitEnabled) return RateLimitResult(true)
        return checkWindow(uploadWindows, ip, config.uploadChunkRequestsPerMinute)
    }

    fun checkDownload(ip: String): RateLimitResult {
        if (!config.rateLimitEnabled) return RateLimitResult(true)
        return checkWindow(downloadWindows, ip, config.downloadRequestsPerMinute)
    }

    fun checkDownloadChunk(ip: String): RateLimitResult {
        if (!config.rateLimitEnabled) return RateLimitResult(true)
        return checkWindow(downloadChunkWindows, ip, config.downloadChunkRequestsPerMinute)
    }

    private fun checkWindow(map: ConcurrentHashMap<String, Window>, ip: String, limitPerMinute: Int): RateLimitResult {
        val nowMinute = Instant.now().epochSecond / 60
        val window = map.compute(ip) { _, current ->
            val w = current ?: Window(nowMinute, 0)
            if (w.windowStartEpochMinute != nowMinute) {
                w.windowStartEpochMinute = nowMinute
                w.count = 0
            }
            w.count += 1
            w
        }!!

        if (window.count <= limitPerMinute) return RateLimitResult(true)

        val secondsIntoWindow = Instant.now().epochSecond % 60
        val retryAfter = max(1, 60 - secondsIntoWindow)
        return RateLimitResult(false, retryAfterSeconds = retryAfter)
    }
}
