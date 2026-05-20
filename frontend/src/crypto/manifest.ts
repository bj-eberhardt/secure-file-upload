export interface PlainManifestFile {
  name: string
  size: number
  type: string
  lastModified: number
  relativePath?: string
}

export interface PlainManifest {
  version: 1
  createdAt: string
  files: PlainManifestFile[]
}

export function createPlainManifest(files: File[]): PlainManifest {
  return {
    version: 1,
    createdAt: new Date().toISOString(),
    files: files.map((file) => ({
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified,
      relativePath: (file as File & { webkitRelativePath?: string }).webkitRelativePath
    }))
  }
}

export interface EncryptedManifestV1 {
  version: 'enc-manifest-v1'
  protocolVersion: string
  // base64url of packed chunk bytes ([u32 nonceLen][u32 cipherLen][nonce][ciphertext])
  payloadB64u: string
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

function base64UrlDecode(value: string): Uint8Array {
  const normalized = value.replaceAll('-', '+').replaceAll('_', '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

function pack(nonce: Uint8Array, ciphertext: Uint8Array): Uint8Array {
  const out = new Uint8Array(8 + nonce.length + ciphertext.length)
  const view = new DataView(out.buffer)
  view.setUint32(0, nonce.length, false)
  view.setUint32(4, ciphertext.length, false)
  out.set(nonce, 8)
  out.set(ciphertext, 8 + nonce.length)
  return out
}

function unpack(bytes: Uint8Array): { nonce: Uint8Array; ciphertext: Uint8Array } {
  if (bytes.length < 8) throw new Error('Invalid manifest payload')
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const nonceLen = view.getUint32(0, false)
  const cipherLen = view.getUint32(4, false)
  const total = 8 + nonceLen + cipherLen
  if (bytes.length !== total) throw new Error('Invalid manifest payload length')
  const nonce = bytes.slice(8, 8 + nonceLen)
  const ciphertext = bytes.slice(8 + nonceLen)
  return { nonce, ciphertext }
}

export async function encryptManifestV1(
  uploadId: string,
  protocolVersion: string,
  key: CryptoKey,
  manifest: PlainManifest
): Promise<EncryptedManifestV1> {
  const plaintext = new TextEncoder().encode(JSON.stringify(manifest))
  const nonce = crypto.getRandomValues(new Uint8Array(12))
  const aad = new TextEncoder().encode(`${uploadId}:manifest:${protocolVersion}`)
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad }, key, plaintext)
  const packed = pack(nonce, new Uint8Array(encrypted))
  return { version: 'enc-manifest-v1', protocolVersion, payloadB64u: base64UrlEncode(packed) }
}

export async function decryptManifestV1(
  uploadId: string,
  key: CryptoKey,
  encrypted: EncryptedManifestV1
): Promise<PlainManifest> {
  if (encrypted.version !== 'enc-manifest-v1') throw new Error('Unsupported manifest version')
  const bytes = base64UrlDecode(encrypted.payloadB64u)
  const { nonce, ciphertext } = unpack(bytes)
  if (nonce.length !== 12) throw new Error('Unsupported manifest nonce length')
  const aad = new TextEncoder().encode(`${uploadId}:manifest:${encrypted.protocolVersion}`)
  const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce, additionalData: aad }, key, ciphertext)
  return JSON.parse(new TextDecoder().decode(new Uint8Array(plaintext))) as PlainManifest
}
