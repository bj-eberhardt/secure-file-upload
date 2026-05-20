/**
 * TODO: Implement streaming unpack/decrypt matching encryptStream.ts.
 * Current placeholder saves encrypted payload to disk until the stream format is finalized.
 */
export async function decryptPayloadPlaceholder(payload: ArrayBuffer, _key: CryptoKey): Promise<Blob> {
  return new Blob([payload], { type: 'application/octet-stream' })
}
