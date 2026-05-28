package app.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiExceptionHandlersTest {
    @Test
    fun illegalArgumentHandler_mapsTo400() {
        val handler = IllegalArgumentHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), IllegalArgumentException("nope"))
        assertEquals(HttpStatus.BAD_REQUEST, resp.status)
        assertEquals(ErrorKey.BAD_REQUEST, resp.body()?.errorKey)
    }

    @Test
    fun illegalArgumentHandler_nullMessage_mapsTo400() {
        val handler = IllegalArgumentHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), IllegalArgumentException())
        assertEquals(HttpStatus.BAD_REQUEST, resp.status)
        assertEquals(ErrorKey.BAD_REQUEST, resp.body()?.errorKey)
    }

    @Test
    fun unknownUploadHandler_mapsTo404() {
        val handler = UnknownUploadHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UnknownUploadException("missing"))
        assertEquals(HttpStatus.NOT_FOUND, resp.status)
        assertEquals(ErrorKey.UNKNOWN_UPLOAD, resp.body()?.errorKey)
    }

    @Test
    fun uploadExpiredHandler_mapsTo410() {
        val handler = UploadExpiredHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UploadExpiredException("expired"))
        assertEquals(HttpStatus.GONE, resp.status)
        assertEquals(ErrorKey.UPLOAD_EXPIRED, resp.body()?.errorKey)
    }

    @Test
    fun uploadConflictHandler_mapsTo409() {
        val handler = UploadConflictHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UploadConflictException("conflict"))
        assertEquals(HttpStatus.CONFLICT, resp.status)
        assertEquals(ErrorKey.UPLOAD_CONFLICT, resp.body()?.errorKey)
    }

    @Test
    fun rateLimitedHandler_mapsTo429AndRetryAfter() {
        val handler = RateLimitedHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), RateLimitedException(retryAfterSeconds = 3))
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.status)
        assertEquals("3", resp.header("Retry-After"))
        assertEquals(ErrorKey.RATE_LIMITED, resp.body()?.errorKey)
    }

    @Test
    fun notFoundHandler_mapsTo404() {
        val handler = NotFoundHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), NotFoundException("missing"))
        assertEquals(HttpStatus.NOT_FOUND, resp.status)
        assertEquals(ErrorKey.NOT_FOUND, resp.body()?.errorKey)
    }
}
