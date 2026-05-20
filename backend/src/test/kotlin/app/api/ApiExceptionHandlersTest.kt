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
    }

    @Test
    fun illegalArgumentHandler_nullMessage_mapsTo400() {
        val handler = IllegalArgumentHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), IllegalArgumentException())
        assertEquals(HttpStatus.BAD_REQUEST, resp.status)
    }

    @Test
    fun unknownUploadHandler_mapsTo404() {
        val handler = UnknownUploadHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UnknownUploadException("missing"))
        assertEquals(HttpStatus.NOT_FOUND, resp.status)
    }

    @Test
    fun uploadExpiredHandler_mapsTo410() {
        val handler = UploadExpiredHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UploadExpiredException("expired"))
        assertEquals(HttpStatus.GONE, resp.status)
    }

    @Test
    fun uploadConflictHandler_mapsTo409() {
        val handler = UploadConflictHandler()
        val resp = handler.handle(HttpRequest.GET<Any>("/x"), UploadConflictException("conflict"))
        assertEquals(HttpStatus.CONFLICT, resp.status)
    }
}
