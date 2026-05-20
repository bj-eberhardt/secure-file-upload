export type FilePayload = {
  name: string
  mimeType: string
  buffer: Buffer
}

export function makeFile(name: string, bytes: number, mimeType = 'application/octet-stream'): FilePayload {
  const buffer = Buffer.alloc(bytes)
  for (let i = 0; i < bytes; i++) buffer[i] = i % 251
  return { name, mimeType, buffer }
}

