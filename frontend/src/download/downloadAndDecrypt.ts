import { decryptPayloadPlaceholder } from '../crypto/decryptStream'

export async function downloadAndDecrypt(uploadId: string, key: CryptoKey): Promise<void> {
  const response = await fetch(`/api/downloads/${uploadId}`)
  if (!response.ok) throw new Error('Download fehlgeschlagen')

  const encrypted = await response.arrayBuffer()
  const decrypted = await decryptPayloadPlaceholder(encrypted, key)

  const url = URL.createObjectURL(decrypted)
  const a = document.createElement('a')
  a.href = url
  a.download = `secure-upload-${uploadId}.zip`
  a.click()
  URL.revokeObjectURL(url)
}
