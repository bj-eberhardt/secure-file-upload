import { decryptChunk, unpackEncryptedChunk } from '../crypto/decryptStream'
import type { PlainManifest } from '../crypto/manifest'
import { deleteDownloadResume, getDownloadResume, putDownloadResume, type DownloadResumeEntry } from './resumeStore'
import { apiBase, API_PREFIX } from '../api/apiConfig'

interface UploadStatusResponse {
  uploadId: string
  completed: boolean
  uploadedChunks: number[]
  protocolVersion: string
  chunkSize: number
  expiresAt?: string | null
  encryptedSize?: number | null
  chunkCount?: number | null
}

async function fetchUploadStatus(uploadId: string): Promise<UploadStatusResponse> {
  const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${uploadId}/status`)
  if (response.status === 410) throw new Error('Upload ist abgelaufen')
  if (!response.ok) throw new Error('Status konnte nicht geladen werden')
  return response.json()
}

async function saveStreamToFileSystemAccess(
  stream: ReadableStream<Uint8Array>,
  suggestedName: string,
  mimeType?: string
): Promise<boolean> {
  const picker = (window as unknown as { showSaveFilePicker?: Function }).showSaveFilePicker
  if (typeof picker !== 'function') return false

  const pickerOptions: any = { suggestedName }
  if (!mimeType || mimeType === 'application/zip') {
    pickerOptions.types = [{ description: 'ZIP', accept: { 'application/zip': ['.zip'] } }]
  }
  let handle: any
  try {
    handle = await picker(pickerOptions)
  } catch (error) {
    const name = (error as any)?.name
    if (name === 'AbortError') throw new Error('Speichern abgebrochen')
    throw error
  }
  const writable = await handle.createWritable()
  const reader = stream.getReader()
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      await writable.write(value)
    }
    await writable.close()
    return true
  } catch (error) {
    try {
      await writable.abort()
    } catch {
      // ignore
    }
    throw error
  } finally {
    try {
      await reader.cancel()
    } catch {
      // ignore
    }
  }
}

async function streamToBlob(stream: ReadableStream<Uint8Array>, type: string): Promise<Blob> {
  const reader = stream.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    chunks.push(value)
    total += value.byteLength
  }
  const out = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    out.set(chunk, offset)
    offset += chunk.byteLength
  }
  return new Blob([out], { type })
}

async function fetchChunkBytes(
  uploadId: string,
  index: number,
  onRateLimitWait?: (seconds: number, detail: string) => void
): Promise<Uint8Array> {
  let attempt = 0
  while (true) {
    const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${uploadId}/chunks/${index}`)
    if (response.status === 410) throw new Error('Upload ist abgelaufen')
    if (response.status === 429) {
      const retryAfter = response.headers.get('Retry-After')
      const seconds = retryAfter ? Number.parseInt(retryAfter, 10) : NaN
      const waitMs = Number.isFinite(seconds) ? Math.max(250, seconds * 1000) : 750
      if (Number.isFinite(seconds) && onRateLimitWait) {
        onRateLimitWait(seconds, `Rate limit – warte ${seconds}s… (Chunk ${index})`)
      } else if (onRateLimitWait) {
        onRateLimitWait(Math.ceil(waitMs / 1000), `Rate limit – warte kurz… (Chunk ${index})`)
      }
      await new Promise((r) => setTimeout(r, waitMs))
      attempt++
      if (attempt < 30) continue
      throw new Error(`Rate limit beim Download (Chunk ${index})`)
    }
    if (!response.ok) throw new Error(`Chunk ${index} download fehlgeschlagen (HTTP ${response.status})`)
    return new Uint8Array(await response.arrayBuffer())
  }
}

async function decryptByFetchingChunks(
  uploadId: string,
  key: CryptoKey,
  status: UploadStatusResponse,
  onProgress?: (percent: number, detail: string) => void
): Promise<ReadableStream<Uint8Array>> {
  const chunkCount = status.chunkCount
  if (typeof chunkCount !== 'number' || chunkCount <= 0) {
    throw new Error('Download kann nicht resümieren: chunkCount fehlt')
  }
  const protocolVersion = status.protocolVersion

  let index = 0
  return new ReadableStream<Uint8Array>({
    async pull(controller) {
      if (index >= chunkCount) {
        controller.close()
        return
      }

      let attempt = 0
      while (true) {
        try {
          const bytes = await fetchChunkBytes(uploadId, index, (seconds, detail) => {
            if (onProgress) onProgress(Math.min(100, (index / chunkCount) * 100), detail)
          })
          const parsed = unpackEncryptedChunk(bytes)
          if (!parsed || parsed.bytesConsumed !== bytes.length) throw new Error(`Invalid chunk ${index} payload`)
          const aad = new TextEncoder().encode(`${uploadId}:${index}:${protocolVersion}`)
          const plaintext = await decryptChunk(key, parsed.chunk, aad)
          controller.enqueue(plaintext)
          index++
          if (onProgress) {
            onProgress(Math.min(100, (index / chunkCount) * 100), `Chunk ${index} / ${chunkCount}`)
          }
          return
        } catch (error) {
          attempt++
          if (attempt >= 3) {
            controller.error(error)
            return
          }
          await new Promise((r) => setTimeout(r, 300 * attempt))
        }
      }
    }
  })
}

export async function downloadAndDecrypt(uploadId: string, key: CryptoKey): Promise<void> {
  const status = await fetchUploadStatus(uploadId)
  const suggested = `secure-upload-${uploadId}.zip`

  const decryptedStream = await decryptByFetchingChunks(uploadId, key, status)
  const saved = await saveStreamToFileSystemAccess(decryptedStream, suggested, 'application/zip')
  if (saved) return

  const decryptedBlob = await streamToBlob(decryptedStream, 'application/zip')

  const url = URL.createObjectURL(decryptedBlob)
  const a = document.createElement('a')
  a.href = url
  a.download = suggested
  a.style.display = 'none'
  a.dataset.testid = 'download:anchor'
  ;(window as any).__pw_lastDownloadName = suggested
  document.body.appendChild(a)
  a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }))
  // Keep the anchor around briefly so E2E tests can assert attributes without racing.
  setTimeout(() => a.remove(), 1000)
  // Revoke after a short delay so Chromium has time to start the download.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export async function downloadAndDecryptWithManifest(
  uploadId: string,
  key: CryptoKey,
  manifest: PlainManifest | null,
  onProgress?: (percent: number, detail: string) => void,
  options?: { preselectedHandle?: FileSystemFileHandle }
): Promise<void> {
  const status = await fetchUploadStatus(uploadId)

  const single = manifest?.files?.length === 1 ? manifest.files[0] : null
  const rawName = single ? (single.relativePath ?? single.name) : `secure-upload-${uploadId}.zip`
  const suggestedName = rawName.split(/[\\/]/).pop() || rawName
  const mimeType = single
    ? single.type && single.type.length > 0
      ? single.type
      : 'application/octet-stream'
    : 'application/zip'

  // E2E/debug hook: remember the intended filename early (independent of whether we use a picker or anchor fallback).
  try {
    ;(window as any).__pw_lastDownloadName = suggestedName
  } catch {
    // ignore
  }

  const resumable = await downloadAndDecryptResumable(
    uploadId,
    key,
    status,
    suggestedName,
    mimeType,
    onProgress,
    options?.preselectedHandle
  )
  if (resumable) return

  const decryptedStream = await decryptByFetchingChunks(uploadId, key, status, onProgress)
  const saved = await saveStreamToFileSystemAccess(decryptedStream, suggestedName, mimeType)
  if (saved) return

  const decryptedBlob = await streamToBlob(decryptedStream, mimeType)

  const url = URL.createObjectURL(decryptedBlob)
  const a = document.createElement('a')
  a.href = url
  a.download = suggestedName
  a.style.display = 'none'
  a.dataset.testid = 'download:anchor'
  ;(window as any).__pw_lastDownloadName = suggestedName
  document.body.appendChild(a)
  a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }))
  setTimeout(() => a.remove(), 1000)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function downloadAndDecryptResumable(
  uploadId: string,
  key: CryptoKey,
  status: UploadStatusResponse,
  suggestedName: string,
  mimeType: string,
  onProgress?: (percent: number, detail: string) => void,
  preselectedHandle?: FileSystemFileHandle
): Promise<boolean> {
  const picker = (window as unknown as { showSaveFilePicker?: Function }).showSaveFilePicker
  if (typeof picker !== 'function') return false

  const chunkCount = status.chunkCount
  if (typeof chunkCount !== 'number' || chunkCount <= 0) return false

  const existing = await getDownloadResume(uploadId)

  let entry: DownloadResumeEntry
  if (existing) {
    entry = existing
  } else {
    let handle: FileSystemFileHandle = preselectedHandle as FileSystemFileHandle
    if (!handle) {
      try {
        handle = (await picker({ suggestedName })) as FileSystemFileHandle
      } catch (error) {
        const name = (error as any)?.name
        if (name === 'AbortError') throw new Error('Speichern abgebrochen')
        throw error
      }
    }
    entry = {
      uploadId,
      nextChunkIndex: 0,
      bytesWritten: 0,
      chunkSize: status.chunkSize,
      protocolVersion: status.protocolVersion,
      suggestedName,
      mimeType,
      handle,
      updatedAt: new Date().toISOString()
    }
  }

  // persist immediately so an early failure can still be resumed; if persistence of the handle
  // is not supported in this browser, we still continue within this session (no cross-reload resume)
  try {
    entry.updatedAt = new Date().toISOString()
    await putDownloadResume(entry)
  } catch {
    // ignore persistence failures (some browsers don't allow storing file handles)
  }

  // permission prompt (needed after reload)
  try {
    const perm = await (entry.handle as any).queryPermission?.({ mode: 'readwrite' })
    if (perm !== 'granted') {
      const req = await (entry.handle as any).requestPermission?.({ mode: 'readwrite' })
      if (req !== 'granted') throw new Error('Keine Berechtigung zum Schreiben der Zieldatei')
    }
  } catch (error) {
    throw error instanceof Error ? error : new Error('Keine Berechtigung zum Schreiben der Zieldatei')
  }

  const resuming = entry.nextChunkIndex > 0 || entry.bytesWritten > 0
  if (resuming) {
    try {
      const file = await entry.handle.getFile()
      if (file.size !== entry.bytesWritten) {
        await deleteDownloadResume(uploadId)
        throw new Error('Fortsetzen ist nicht möglich: Zieldatei passt nicht mehr (bitte "Neu starten")')
      }
    } catch (error) {
      if (error instanceof Error) throw error
      throw new Error('Fortsetzen ist nicht möglich: Zieldatei kann nicht geprüft werden (bitte "Neu starten")')
    }
  }

  const writable = await (entry.handle as any).createWritable({ keepExistingData: resuming })
  if (resuming) await writable.seek(entry.bytesWritten)

  try {
    if (onProgress) {
      onProgress(Math.min(100, (entry.nextChunkIndex / chunkCount) * 100), `Chunk ${entry.nextChunkIndex} / ${chunkCount}`)
    }
    while (entry.nextChunkIndex < chunkCount) {
      const index = entry.nextChunkIndex
      const bytes = await fetchChunkBytes(uploadId, index, (seconds, detail) => {
        if (onProgress) onProgress(Math.min(100, (entry.nextChunkIndex / chunkCount) * 100), detail)
      })
      const parsed = unpackEncryptedChunk(bytes)
      if (!parsed || parsed.bytesConsumed !== bytes.length) throw new Error(`Invalid chunk ${index} payload`)
      const aad = new TextEncoder().encode(`${uploadId}:${index}:${status.protocolVersion}`)
      const plaintext = await decryptChunk(key, parsed.chunk, aad)
      await writable.write(plaintext)

      entry.nextChunkIndex++
      entry.bytesWritten += plaintext.byteLength
      entry.updatedAt = new Date().toISOString()
      try {
        await putDownloadResume(entry)
      } catch {
        // ignore
      }
      if (onProgress) {
        onProgress(Math.min(100, (entry.nextChunkIndex / chunkCount) * 100), `Chunk ${entry.nextChunkIndex} / ${chunkCount}`)
      }
    }

    await writable.close()
    try {
      await deleteDownloadResume(uploadId)
    } catch {
      // ignore
    }
    return true
  } catch (error) {
    try {
      // close commits partial progress; abort would discard written data in many browsers
      await writable.close()
    } catch {
      // ignore
    }
    // keep resume entry for later retry
    throw error
  }
}
