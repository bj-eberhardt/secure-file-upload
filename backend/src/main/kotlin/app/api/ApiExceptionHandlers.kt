package app.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Produces
@Singleton
class IllegalArgumentHandler : ExceptionHandler<IllegalArgumentException, HttpResponse<JsonError>> {
    override fun handle(request: HttpRequest<*>, exception: IllegalArgumentException): HttpResponse<JsonError> =
        HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(
            JsonError(exception.message ?: "Bad Request").link(Link.SELF, Link.of(request.uri))
        )
}

@Produces
@Singleton
class UnknownUploadHandler : ExceptionHandler<UnknownUploadException, HttpResponse<JsonError>> {
    override fun handle(request: HttpRequest<*>, exception: UnknownUploadException): HttpResponse<JsonError> =
        HttpResponse.status<JsonError>(HttpStatus.NOT_FOUND).body(
            JsonError(exception.message).link(Link.SELF, Link.of(request.uri))
        )
}

@Produces
@Singleton
class UploadExpiredHandler : ExceptionHandler<UploadExpiredException, HttpResponse<JsonError>> {
    override fun handle(request: HttpRequest<*>, exception: UploadExpiredException): HttpResponse<JsonError> =
        HttpResponse.status<JsonError>(HttpStatus.GONE).body(
            JsonError(exception.message).link(Link.SELF, Link.of(request.uri))
        )
}

@Produces
@Singleton
class UploadConflictHandler : ExceptionHandler<UploadConflictException, HttpResponse<JsonError>> {
    override fun handle(request: HttpRequest<*>, exception: UploadConflictException): HttpResponse<JsonError> =
        HttpResponse.status<JsonError>(HttpStatus.CONFLICT).body(
            JsonError(exception.message).link(Link.SELF, Link.of(request.uri))
        )
}
