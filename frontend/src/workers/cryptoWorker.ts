import { BlobReader, ZipWriterStream } from '@zip.js/zip.js'
import { encryptChunk as encryptChunkV1, packEncryptedChunk } from '../crypto/encryptStream'
import { createPlainManifest, encryptManifestV1 } from '../crypto/manifest'
import { API_PREFIX } from '../api/apiConfig'

type UploadInit = {
  uploadId: string
  chunkSize: number
  protocolVersion: string
}

type WorkerUploadRequest = {
  type: 'upload'
  apiBase?: string
  init: UploadInit
  files: File[]
  keyRaw: ArrayBuffer
}

type WorkerAbortRequest = { type: 'abort' }

type WorkerRequest = WorkerUploadRequest | WorkerAbortRequest

type WorkerProgress = {
  type: 'progress'
  messageKey: string
  messageParams?: Record<string, unknown>
  uploadedChunks: number
  totalChunks?: number
  percent?: number
  detail?: string
}
type WorkerResult = {
  type: 'result'
  result: {
    chunkCount: number
    encryptedSize: number
    encryptedManifest: string
    chunkSize: number
    protocolVersion: string
  }
}
type WorkerError = { type: 'error'; message: string; errorKey?: string }

let abortController: AbortController | null = null
let abortRequested = false

function post(msg: WorkerProgress | WorkerResult | WorkerError) {
  ;(self as unknown as Worker).postMessage(msg)
}

function concatBytes(a: Uint8Array, b: Uint8Array): Uint8Array {
  const out = new Uint8Array(a.length + b.length)
  out.set(a, 0)
  out.set(b, a.length)
  return out
}

async function fetchUploadedChunks(apiBase: string, uploadId: string): Promise<Set<number>> {
  const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${uploadId}/status`, { signal: abortController?.signal })
  if (!response.ok) return new Set()
  const json = (await response.json()) as { uploadedChunks?: number[] }
  return new Set(json.uploadedChunks ?? [])
}

async function createZipReadableStream(files: File[]): Promise<ReadableStream<Uint8Array>> {
  const stream = new ZipWriterStream({ level: 0 })
  const zipWriter = stream.zipWriter

  ;(async () => {
    try {
      for (const file of files) {
        const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath
        const name = relativePath && relativePath.length > 0 ? relativePath : file.name
        await zipWriter.add(name, new BlobReader(file), {
          lastModDate: new Date(file.lastModified)
        })
      }
      await zipWriter.close()
    } catch {
      try {
        await zipWriter.close()
      } catch {
      }
    }
  })()

  return stream.readable
}

function createSingleFileReadableStream(file: File): ReadableStream<Uint8Array> {
  return file.stream() as unknown as ReadableStream<Uint8Array>
}

async function uploadEncrypted(
  apiBase: string,
  init: UploadInit,
  files: File[],
  keyRaw: ArrayBuffer
): Promise<WorkerResult['result']> {
  abortController = new AbortController()
  abortRequested = false

  const keyBytes = new Uint8Array(keyRaw)
  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt'])
  const uploaded = await fetchUploadedChunks(apiBase, init.uploadId)
  const estimatedTotalBytes = files.reduce((sum, f) => sum + f.size, 0)
  let processedPlainBytes = 0

  post({ type: 'progress', messageKey: 'upload.creatingManifest', uploadedChunks: 0, detail: '' })
  const plainManifest = createPlainManifest(files)
  const encryptedManifestObj = await encryptManifestV1(init.uploadId, init.protocolVersion, key, plainManifest)
  const encryptedManifest = JSON.stringify(encryptedManifestObj)

  if (files.length === 1) {
    post({ type: 'progress', messageKey: 'upload.readingFileStream', uploadedChunks: 0, detail: '' })
  } else {
    post({ type: 'progress', messageKey: 'upload.creatingZipStream', uploadedChunks: 0, detail: '' })
  }
  const uploadStream = files.length === 1 ? createSingleFileReadableStream(files[0]) : await createZipReadableStream(files)
  const reader = uploadStream.getReader()

  let index = 0
  let encryptedSize = 0
  let buffer = new Uint8Array(0)

  while (true) {
    if (abortRequested || abortController.signal.aborted) throw new Error('Abgebrochen')
    while (buffer.length >= init.chunkSize) {
      const plaintext = buffer.slice(0, init.chunkSize)
      buffer = buffer.slice(init.chunkSize)
      processedPlainBytes += plaintext.byteLength

      if (uploaded.has(index)) {
        index++
        const percent = estimatedTotalBytes > 0 ? Math.min(99, (processedPlainBytes / estimatedTotalBytes) * 100) : undefined
        post({
          type: 'progress',
          messageKey: 'upload.chunkAlreadyPresent',
          messageParams: { chunk: index + 1 },
          uploadedChunks: index,
          percent,
          detail: `${Math.round(processedPlainBytes / (1024 * 1024))} / ${Math.round(estimatedTotalBytes / (1024 * 1024))} MiB`
        })
        continue
      }

      const aad = new TextEncoder().encode(`${init.uploadId}:${index}:${init.protocolVersion}`)
      const encryptedChunk = await encryptChunkV1(key, index, plaintext, aad)
      const packed = packEncryptedChunk(encryptedChunk)
      const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${init.uploadId}/chunks/${index}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream' },
        body: packed,
        signal: abortController.signal
      })
      if (!response.ok) {
        const retryAfter = response.headers.get('Retry-After')
        if (response.status === 429 && retryAfter) throw new Error(`RATE_LIMITED:${retryAfter}`)
        let errorKey: string | undefined
        let message = `Chunk upload failed (HTTP ${response.status})`
        try {
          const json: any = await response.json()
          errorKey = typeof json?.errorKey === 'string' ? json.errorKey : undefined
          message = typeof json?.message === 'string' ? json.message : message
        } catch {
        }
        throw Object.assign(new Error(message), { errorKey })
      }
      encryptedSize += packed.byteLength
      index++
      const percent = estimatedTotalBytes > 0 ? Math.min(99, (processedPlainBytes / estimatedTotalBytes) * 100) : undefined
      post({
        type: 'progress',
        messageKey: 'progress.chunkUploaded',
        messageParams: { chunk: index },
        uploadedChunks: index,
        percent,
        detail: `${Math.round(processedPlainBytes / (1024 * 1024))} / ${Math.round(estimatedTotalBytes / (1024 * 1024))} MiB`
      })
    }

    const { value, done } = await reader.read()
    if (done) break
    buffer = concatBytes(buffer, value)
  }

  if (buffer.length > 0) {
    const plaintext = buffer
    processedPlainBytes += plaintext.byteLength
    if (!uploaded.has(index)) {
      const aad = new TextEncoder().encode(`${init.uploadId}:${index}:${init.protocolVersion}`)
      const encryptedChunk = await encryptChunkV1(key, index, plaintext, aad)
      const packed = packEncryptedChunk(encryptedChunk)
      const response = await fetch(`${apiBase}${API_PREFIX}/uploads/${init.uploadId}/chunks/${index}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream' },
        body: packed,
        signal: abortController.signal
      })
      if (!response.ok) {
        const retryAfter = response.headers.get('Retry-After')
        if (response.status === 429 && retryAfter) throw new Error(`RATE_LIMITED:${retryAfter}`)
        let errorKey: string | undefined
        let message = `Chunk upload failed (HTTP ${response.status})`
        try {
          const json: any = await response.json()
          errorKey = typeof json?.errorKey === 'string' ? json.errorKey : undefined
          message = typeof json?.message === 'string' ? json.message : message
        } catch {
        }
        throw Object.assign(new Error(message), { errorKey })
      }
      encryptedSize += packed.byteLength
      index++
      const percent = estimatedTotalBytes > 0 ? Math.min(99, (processedPlainBytes / estimatedTotalBytes) * 100) : undefined
      post({
        type: 'progress',
        messageKey: 'progress.chunkUploaded',
        messageParams: { chunk: index },
        uploadedChunks: index,
        percent,
        detail: `${Math.round(processedPlainBytes / (1024 * 1024))} / ${Math.round(estimatedTotalBytes / (1024 * 1024))} MiB`
      })
    } else {
      index++
      const percent = estimatedTotalBytes > 0 ? Math.min(99, (processedPlainBytes / estimatedTotalBytes) * 100) : undefined
      post({
        type: 'progress',
        messageKey: 'upload.chunkAlreadyPresent',
        messageParams: { chunk: index },
        uploadedChunks: index,
        percent,
        detail: `${Math.round(processedPlainBytes / (1024 * 1024))} / ${Math.round(estimatedTotalBytes / (1024 * 1024))} MiB`
      })
    }
  }

  post({ type: 'progress', messageKey: 'upload.finalizing', uploadedChunks: index, percent: 100, detail: '' })

  return {
    chunkCount: index,
    encryptedSize,
    encryptedManifest,
    chunkSize: init.chunkSize,
    protocolVersion: init.protocolVersion
  }
}

self.addEventListener('message', async (event: MessageEvent<WorkerRequest>) => {
  const msg = event.data
  if (msg.type === 'abort') {
    abortRequested = true
    abortController?.abort()
    abortController = null
    post({ type: 'error', errorKey: 'ABORTED', message: 'Aborted' })
    return
  }

  try {
    const apiBase = msg.apiBase ?? ''
    const result = await uploadEncrypted(apiBase, msg.init, msg.files, msg.keyRaw)
    post({ type: 'result', result })
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('RATE_LIMITED:')) {
      const retryAfterSeconds = error.message.split(':')[1] ?? ''
      post({ type: 'error', errorKey: 'RATE_LIMITED', message: `Too many requests. Retry in ${retryAfterSeconds}s.` })
      return
    }
    const errAny = error as any
    post({
      type: 'error',
      errorKey: typeof errAny?.errorKey === 'string' ? errAny.errorKey : undefined,
      message: error instanceof Error ? error.message : 'Unknown error'
    })
  }
})

export {}
