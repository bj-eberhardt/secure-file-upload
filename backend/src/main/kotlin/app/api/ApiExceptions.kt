package app.api

class UnknownUploadException(
    override val message: String = "Unknown upload id"
) : RuntimeException(message)

class UploadExpiredException(
    override val message: String = "Upload expired"
) : RuntimeException(message)

class UploadConflictException(
    override val message: String
) : RuntimeException(message)
