export type FileFingerprint = {
  algo: 'sha256'
  valueB64u: string
  fileCount: number
  totalBytes: number
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

function normalizePath(p?: string): string {
  if (!p) return ''
  return p.replaceAll('\\', '/')
}

export async function computeFileFingerprint(files: File[]): Promise<FileFingerprint> {
  const entries = files
    .map((file) => {
      const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath
      return {
        name: file.name,
        relativePath: normalizePath(relativePath),
        size: file.size,
        lastModified: file.lastModified,
        type: file.type ?? ''
      }
    })
    .sort((a, b) => {
      const ak = `${a.relativePath}|${a.name}`
      const bk = `${b.relativePath}|${b.name}`
      return ak.localeCompare(bk)
    })

  const totalBytes = entries.reduce((sum, e) => sum + e.size, 0)

  const canonical = JSON.stringify({
    v: 1,
    files: entries
  })
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical))
  return {
    algo: 'sha256',
    valueB64u: base64UrlEncode(new Uint8Array(digest)),
    fileCount: entries.length,
    totalBytes
  }
}

