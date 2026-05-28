package app.api

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ApiErrorResponse(
    val errorKey: ErrorKey,
    val message: String
)

