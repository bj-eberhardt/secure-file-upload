package app.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UploadDtosTest {
    @Test
    fun uploadStatusResponse_instantiatesAndExposesFields() {
        val dto = UploadStatusResponse(
            uploadId = "id",
            completed = true,
            uploadedChunks = listOf(0, 1),
            protocolVersion = "v1",
            chunkSize = 123,
            expiresAt = "2020-01-01T00:00:00Z",
            encryptedSize = 456,
            chunkCount = 2
        )
        assertEquals("id", dto.uploadId)
        assertEquals(true, dto.completed)
        assertEquals(listOf(0, 1), dto.uploadedChunks)
        assertEquals("v1", dto.protocolVersion)
        assertEquals(123, dto.chunkSize)
        assertEquals(456, dto.encryptedSize)
        assertEquals(2, dto.chunkCount)
    }
}

