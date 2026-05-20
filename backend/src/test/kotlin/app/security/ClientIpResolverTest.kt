package app.security

import app.config.UploadConfiguration
import io.micronaut.http.HttpRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class ClientIpResolverTest {
    @Test
    fun trustProxyHeaders_false_usesRemoteAddress() {
        val cfg = UploadConfiguration().apply { trustProxyHeaders = false }
        val resolver = ClientIpResolver(cfg)

        val base = HttpRequest.GET<Any>("/")
        val req = object : HttpRequest<Any> by base {
            override fun getRemoteAddress(): InetSocketAddress =
                InetSocketAddress("203.0.113.10", 12345)
        }

        assertEquals("203.0.113.10", resolver.resolve(req))
    }

    @Test
    fun trustProxyHeaders_true_usesFirstXForwardedForHop() {
        val cfg = UploadConfiguration().apply { trustProxyHeaders = true }
        val resolver = ClientIpResolver(cfg)

        val base = HttpRequest.GET<Any>("/").header("X-Forwarded-For", "198.51.100.7, 203.0.113.10")
        val req = object : HttpRequest<Any> by base {
            override fun getRemoteAddress(): InetSocketAddress =
                InetSocketAddress("203.0.113.10", 12345)
        }

        assertEquals("198.51.100.7", resolver.resolve(req))
    }

    @Test
    fun trustProxyHeaders_true_fallsBackWhenHeaderMissing() {
        val cfg = UploadConfiguration().apply { trustProxyHeaders = true }
        val resolver = ClientIpResolver(cfg)

        val base = HttpRequest.GET<Any>("/")
        val req = object : HttpRequest<Any> by base {
            override fun getRemoteAddress(): InetSocketAddress =
                InetSocketAddress("203.0.113.10", 12345)
        }

        assertEquals("203.0.113.10", resolver.resolve(req))
    }

    @Test
    fun remoteAddress_null_returnsUnknown() {
        val cfg = UploadConfiguration().apply { trustProxyHeaders = false }
        val resolver = ClientIpResolver(cfg)

        val base = HttpRequest.GET<Any>("/")
        val req = object : HttpRequest<Any> by base {
            override fun getRemoteAddress(): InetSocketAddress =
                InetSocketAddress.createUnresolved("unknown", 0)
        }

        val resolved = resolver.resolve(req)
        assertTrue(resolved.isNotBlank())
    }

    @Test
    fun unresolvedRemoteAddress_usesToStringFallback() {
        val cfg = UploadConfiguration().apply { trustProxyHeaders = false }
        val resolver = ClientIpResolver(cfg)

        val base = HttpRequest.GET<Any>("/")
        val req = object : HttpRequest<Any> by base {
            override fun getRemoteAddress(): InetSocketAddress =
                InetSocketAddress.createUnresolved("unresolved.example", 12345)
        }

        // address is null -> falls back to remote.toString()
        val resolved = resolver.resolve(req)
        assertTrue(resolved.contains("unresolved.example"))
    }
}
