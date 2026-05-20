import { encryptChunk, packEncryptedChunk } from '../crypto/encryptStream'
import { createZipPlaceholder } from '../zip/zipStreamWriter'

export interface InitUploadResponse {
  uploadId: string
  chunkSize: number
  uploadUrl: string
  completeUrl: string
}

export interface UploadEncryptedResult {
  chunkCount: number
  encryptedSize: number
  encryptedManifest?: string
}

export async function initUpload(): Promise<InitUploadResponse> {
  const response = await fetch('/api/uploads/init', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({})
  })
  if (!response.ok) throw new Error('Upload konnte nicht initialisiert werden')
  return response.json()
}

export async function uploadEncryptedChunks(
  init: InitUploadResponse,
  files: File[],
  key: CryptoKey,
  onProgress: (message: string) => void
): Promise<UploadEncryptedResult> {
  const zip = await createZipPlaceholder(files)
  const bytes = new Uint8Array(await zip.arrayBuffer())
  let offset = 0
  let index = 0
  let encryptedSize = 0

  while (offset < bytes.length) {
    const plaintext = bytes.slice(offset, offset + init.chunkSize)
    const aad = new TextEncoder().encode(`${init.uploadId}:${index}:v1`)
    const encrypted = await encryptChunk(key, index, plaintext, aad)
    const packed = packEncryptedChunk(encrypted)

    const response = await fetch(`/api/uploads/${init.uploadId}/chunks/${index}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: packed
    })
    if (!response.ok) throw new Error(`Chunk ${index} konnte nicht hochgeladen werden`)

    encryptedSize += packed.byteLength
    offset += init.chunkSize
    index++
    onProgress(`Chunk ${index} hochgeladen`)
  }

  return { chunkCount: index, encryptedSize }
}

export async function completeUpload(uploadId: string, result: UploadEncryptedResult): Promise<{ downloadPath: string }> {
  const response = await fetch(`/api/uploads/${uploadId}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(result)
  })
  if (!response.ok) throw new Error('Upload konnte nicht abgeschlossen werden')
  return response.json()
}
