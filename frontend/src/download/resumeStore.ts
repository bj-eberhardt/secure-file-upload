export type DownloadResumeEntry = {
  uploadId: string
  nextChunkIndex: number
  bytesWritten: number
  chunkSize: number
  protocolVersion: string
  suggestedName: string
  mimeType: string
  handle: FileSystemFileHandle
  updatedAt: string
}

const memoryStore = new Map<string, DownloadResumeEntry>()

const DB_NAME = 'secure-upload'
const DB_VERSION = 1
const STORE = 'downloadResume'

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'uploadId' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function getDownloadResume(uploadId: string): Promise<DownloadResumeEntry | null> {
  const cached = memoryStore.get(uploadId)
  if (cached) return cached

  let db: IDBDatabase
  try {
    db = await openDb()
  } catch {
    return null
  }

  return new Promise<DownloadResumeEntry | null>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly')
    const store = tx.objectStore(STORE)
    const req = store.get(uploadId)
    req.onsuccess = () => {
      const entry = (((req.result as any) as DownloadResumeEntry) ?? null) as DownloadResumeEntry | null
      if (entry) memoryStore.set(uploadId, entry)
      resolve(entry)
    }
    req.onerror = () => reject(req.error)
  }).finally(() => db.close())
}

export async function putDownloadResume(entry: DownloadResumeEntry): Promise<void> {
  memoryStore.set(entry.uploadId, entry)

  let db: IDBDatabase
  try {
    db = await openDb()
  } catch {
    return
  }

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.objectStore(STORE).put(entry)
  }).finally(() => db.close())
}

export async function deleteDownloadResume(uploadId: string): Promise<void> {
  memoryStore.delete(uploadId)

  let db: IDBDatabase
  try {
    db = await openDb()
  } catch {
    return
  }

  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.objectStore(STORE).delete(uploadId)
  }).finally(() => db.close())
}
