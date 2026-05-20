export interface PackedEncryptedChunk {
  nonce: Uint8Array
  ciphertext: Uint8Array
}

function readU32be(bytes: Uint8Array, offset: number): number {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  return view.getUint32(offset, false)
}

function concatBytes(a: Uint8Array, b: Uint8Array): Uint8Array {
  const out = new Uint8Array(a.length + b.length)
  out.set(a, 0)
  out.set(b, a.length)
  return out
}

export function unpackEncryptedChunk(buffer: Uint8Array): { chunk: PackedEncryptedChunk; bytesConsumed: number } | null {
  // v1+: [u32 nonceLen BE][u32 cipherLen BE][nonce][ciphertext]
  if (buffer.length < 8) return null
  const nonceLen = readU32be(buffer, 0)
  const cipherLen = readU32be(buffer, 4)
  const headerLen = 8
  const totalLen = headerLen + nonceLen + cipherLen
  if (nonceLen <= 0 || cipherLen <= 0) throw new Error('Invalid chunk header')
  if (buffer.length < totalLen) return null
  const nonce = buffer.slice(headerLen, headerLen + nonceLen)
  const ciphertext = buffer.slice(headerLen + nonceLen, totalLen)
  return { chunk: { nonce, ciphertext }, bytesConsumed: totalLen }
}

export async function decryptChunk(
  key: CryptoKey,
  packed: PackedEncryptedChunk,
  aad: Uint8Array
): Promise<Uint8Array> {
  if (packed.nonce.length !== 12) throw new Error(`Unsupported nonce length: ${packed.nonce.length}`)
  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: packed.nonce, additionalData: aad },
    key,
    packed.ciphertext
  )
  return new Uint8Array(plaintext)
}

export function decryptPackedChunksStream(
  encryptedStream: ReadableStream<Uint8Array>,
  key: CryptoKey,
  uploadId: string,
  protocolVersion: string
): ReadableStream<Uint8Array> {
  let buffer = new Uint8Array(0)
  let index = 0

  const reader = encryptedStream.getReader()

  return new ReadableStream<Uint8Array>({
    async pull(controller) {
      while (true) {
        const parsed = unpackEncryptedChunk(buffer)
        if (parsed) {
          const aad = new TextEncoder().encode(`${uploadId}:${index}:${protocolVersion}`)
          const plaintext = await decryptChunk(key, parsed.chunk, aad)
          buffer = buffer.slice(parsed.bytesConsumed)
          index++
          controller.enqueue(plaintext)
          return
        }

        const { value, done } = await reader.read()
        if (done) {
          if (buffer.length !== 0) {
            controller.error(new Error('Truncated encrypted stream'))
            return
          }
          controller.close()
          return
        }
        buffer = concatBytes(buffer, value)
      }
    },
    async cancel(reason) {
      try {
        await reader.cancel(reason)
      } catch {
        // ignore
      }
    }
  })
}

