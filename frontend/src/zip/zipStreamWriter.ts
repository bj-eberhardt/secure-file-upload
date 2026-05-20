import { BlobReader, BlobWriter, ZipWriter } from '@zip.js/zip.js'

/**
 * Creates a real ZIP archive blob from the selected files.
 *
 * Note: This is a correct MVP implementation but not fully streaming-optimized yet.
 * The next iteration can switch to a stream pipeline (ZIP -> encrypt -> upload) with backpressure.
 */
export async function createZipBlob(files: File[]): Promise<Blob> {
  const writer = new ZipWriter(new BlobWriter('application/zip'))

  try {
    for (const file of files) {
      const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath
      const name = relativePath && relativePath.length > 0 ? relativePath : file.name
      await writer.add(name, new BlobReader(file))
    }
    return await writer.close()
  } catch (error) {
    try {
      await writer.close()
    } catch {
      // ignore
    }
    throw error
  }
}
