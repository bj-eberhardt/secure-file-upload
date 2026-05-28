package app.api

class UnknownUploadException(
    override val message: String = "Unknown upload id",
    val errorKey: ErrorKey = ErrorKey.UNKNOWN_UPLOAD
) : RuntimeException(message)

class UploadExpiredException(
    override val message: String = "Upload expired",
    val errorKey: ErrorKey = ErrorKey.UPLOAD_EXPIRED
) : RuntimeException(message)

class UploadConflictException(
    override val message: String,
    val errorKey: ErrorKey = ErrorKey.UPLOAD_CONFLICT
) : RuntimeException(message)

class RateLimitedException(
    val retryAfterSeconds: Long,
    override val message: String = "Too many requests",
    val errorKey: ErrorKey = ErrorKey.RATE_LIMITED
) : RuntimeException(message)

class NotFoundException(
    override val message: String = "Not found",
    val errorKey: ErrorKey = ErrorKey.NOT_FOUND
) : RuntimeException(message)
