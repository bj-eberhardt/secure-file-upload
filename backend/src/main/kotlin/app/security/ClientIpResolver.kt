package app.security

import app.config.UploadConfiguration
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton

@Singleton
class ClientIpResolver(private val config: UploadConfiguration) {
    fun resolve(request: HttpRequest<*>): String {
        if (config.trustProxyHeaders) {
            val forwarded = request.headers["X-Forwarded-For"]
            if (!forwarded.isNullOrBlank()) {
                val first = forwarded.split(',').firstOrNull()?.trim()
                if (!first.isNullOrBlank()) return first
            }
        }
        val remote = request.remoteAddress
        return remote?.address?.hostAddress
            ?: remote?.toString()
            ?: "unknown"
    }
}

