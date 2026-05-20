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
