package app.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Produces
@Singleton
class IllegalArgumentHandler : ExceptionHandler<IllegalArgumentException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: IllegalArgumentException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(ErrorKey.BAD_REQUEST, exception.message ?: "Bad request")
        )
}

@Produces
@Singleton
class UnknownUploadHandler : ExceptionHandler<UnknownUploadException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: UnknownUploadException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(exception.errorKey, exception.message)
        )
}

@Produces
@Singleton
class UploadExpiredHandler : ExceptionHandler<UploadExpiredException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: UploadExpiredException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.GONE).body(
            ApiErrorResponse(exception.errorKey, exception.message)
        )
}

@Produces
@Singleton
class UploadConflictHandler : ExceptionHandler<UploadConflictException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: UploadConflictException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.CONFLICT).body(
            ApiErrorResponse(exception.errorKey, exception.message)
        )
}

@Produces
@Singleton
class RateLimitedHandler : ExceptionHandler<RateLimitedException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: RateLimitedException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", exception.retryAfterSeconds.toString())
            .body(ApiErrorResponse(exception.errorKey, exception.message))
}

@Produces
@Singleton
class NotFoundHandler : ExceptionHandler<NotFoundException, HttpResponse<ApiErrorResponse>> {
    override fun handle(request: HttpRequest<*>, exception: NotFoundException): HttpResponse<ApiErrorResponse> =
        HttpResponse.status<ApiErrorResponse>(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse(exception.errorKey, exception.message))
}
