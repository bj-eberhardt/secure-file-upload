package app.security

import io.micronaut.context.env.Environment
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.HttpRequest
import org.reactivestreams.Publisher

@Filter("/**")
class SecurityHeadersFilter(private val environment: Environment) : HttpServerFilter {
    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return Publishers.map(chain.proceed(request)) { response ->
            if (isProd()) {
                response.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                response.header("X-Content-Type-Options", "nosniff")
                response.header("Referrer-Policy", "no-referrer")
                response.header(
                    "Content-Security-Policy",
                    "default-src 'self'; " +
                        "base-uri 'none'; object-src 'none'; frame-ancestors 'none'; " +
                        "script-src 'self'; connect-src 'self'; " +
                        "img-src 'self' data:; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "form-action 'self'"
                )
            }
            response
        }
    }

    private fun isProd(): Boolean =
        environment.activeNames.contains("production") || environment.activeNames.contains("prod")
}
