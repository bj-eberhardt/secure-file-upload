export interface EncryptedChunk {
  index: number
  nonce: Uint8Array
  ciphertext: Uint8Array
}

/**
 * Skeleton implementation: encrypts each plaintext chunk independently with AES-GCM.
 * TODO: Replace with a true stream pipeline and AAD including uploadId, version, and chunk index.
 */
export async function encryptChunk(
  key: CryptoKey,
  index: number,
  plaintext: Uint8Array,
  aad?: Uint8Array
): Promise<EncryptedChunk> {
  const nonce = crypto.getRandomValues(new Uint8Array(12))
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce, additionalData: aad },
    key,
    plaintext
  )
  return { index, nonce, ciphertext: new Uint8Array(encrypted) }
}

export function packEncryptedChunk(chunk: EncryptedChunk): Uint8Array {
  const out = new Uint8Array(4 + chunk.nonce.length + chunk.ciphertext.length)
  const view = new DataView(out.buffer)
  view.setUint32(0, chunk.nonce.length, false)
  out.set(chunk.nonce, 4)
  out.set(chunk.ciphertext, 4 + chunk.nonce.length)
  return out
}
