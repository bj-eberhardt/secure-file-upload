/**
 * PQC abstraction seam.
 *
 * Browser WebCrypto does not yet expose a universal production ML-KEM/Kyber API.
 * Keep post-quantum support behind this interface so a vetted WASM implementation
 * can be added later without touching upload/download flows.
 */
export interface HybridWrappedKey {
  version: 'hybrid-v1'
  classicalPart: string
  pqcPart?: string
}

export async function wrapKeyHybridPlaceholder(): Promise<HybridWrappedKey> {
  throw new Error('TODO: Add vetted ML-KEM/WASM implementation here')
}
