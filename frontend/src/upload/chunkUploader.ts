import { encryptChunk, packEncryptedChunk } from '../crypto/encryptStream'
import { createPlainManifest, encryptManifestV1 } from '../crypto/manifest'
import { createZipBlob } from '../zip/zipStreamWriter'
import { apiBase, API_PREFIX } from '../api/apiConfig'

export interface InitUploadResponse {
  uploadId: string
  chunkSize: number
  protocolVersion: string
  uploadUrl: string
  completeUrl: string
}

export interface UploadEncryptedResult {
  chunkCount: number
  encryptedSize: number
  encryptedManifest: string
  chunkSize: number
  protocolVersion: string
}

export async function initUpload(): Promise<InitUploadResponse> {
  const response = await fetch(`${apiBase}${API_PREFIX}/uploads/init`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({})
  })
  if (!response.ok) {
    const retryAfter = response.headers.get('Retry-After')
    if (response.status === 429 && retryAfter) {
      throw new Error(`Rate limit: bitte in ${retryAfter}s erneut versuchen`)
    }
    if (response.status === 410) throw new Error('Upload ist abgelaufen (bitte neu starten)')
    let detail = ''
    try {
      const json: any = await response.json()
      detail = typeof json?.message === 'string' ? json.message : ''
    } catch {
      // ignore
    }
    throw new Error(detail ? `Upload konnte nicht initialisiert werden: ${detail}` : 'Upload konnte nicht initialisiert werden')
  }
  return response.json()
}

export async function uploadEncryptedChunks(
  init: InitUploadResponse,
  files: File[],
  key: CryptoKey,
  onProgress: (message: string) => void
): Promise<UploadEncryptedResult> {
  const plainManifest = createPlainManifest(files)
  const encryptedManifestObj = await encryptManifestV1(init.uploadId, init.protocolVersion, key, plainManifest)
  const encryptedManifest = JSON.stringify(encryptedManifestObj)

  const zip = await createZipBlob(files)
  const bytes = new Uint8Array(await zip.arrayBuffer())
  let offset = 0
  let index = 0
  let encryptedSize = 0

  while (offset < bytes.length) {
    const plaintext = bytes.slice(offset, offset + init.chunkSize)
    const aad = new TextEncoder().encode(`${init.uploadId}:${index}:${init.protocolVersion}`)
    const encrypted = await encryptChunk(key, index, plaintext, aad)
    const packed = packEncryptedChunk(encrypted)

    const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${init.uploadId}/chunks/${index}`, {
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

  return { chunkCount: index, encryptedSize, encryptedManifest, chunkSize: init.chunkSize, protocolVersion: init.protocolVersion }
}

export async function completeUpload(uploadId: string, result: UploadEncryptedResult): Promise<{ downloadPath: string }> {
  const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${uploadId}/complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(result)
  })
  if (!response.ok) throw new Error('Upload konnte nicht abgeschlossen werden')
  return response.json()
}
