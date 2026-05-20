export type ParsedShareLink = {
  url: string
  uploadId: string
  key: string
}

export async function readShareLink(page: { getByTestId: (id: string) => any }): Promise<string> {
  const locator = page.getByTestId('upload:share-link')
  const value = await locator.inputValue()
  return value.trim()
}

export function parseShareLink(url: string): ParsedShareLink {
  const u = new URL(url)
  const parts = u.pathname.split('/').filter(Boolean)
  const uploadId = parts[1] ?? ''
  const key = new URLSearchParams(u.hash.slice(1)).get('key') ?? ''
  if (!uploadId) throw new Error(`Invalid share link (missing upload id): ${url}`)
  if (!key) throw new Error(`Invalid share link (missing key): ${url}`)
  return { url, uploadId, key }
}

