/**
 * TODO: Implement true streaming ZIP generation.
 *
 * Recommended direction:
 * - Use Web Streams API.
 * - Avoid building a full ZIP in RAM.
 * - Include encrypted metadata only, or place metadata inside the encrypted archive.
 */
export async function createZipPlaceholder(files: File[]): Promise<Blob> {
  // Placeholder: concatenate file bytes. Replace with streaming ZIP implementation.
  return new Blob(files, { type: 'application/octet-stream' })
}
