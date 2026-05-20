import type { Page } from '@playwright/test'

export async function disableSaveFilePicker(page: Page): Promise<void> {
  await page.addInitScript(() => {
    ;(window as any).showSaveFilePicker = undefined
  })
}

type FakeWritable = {
  write: (chunk: Uint8Array) => Promise<void>
  close: () => Promise<void>
  abort: () => Promise<void>
  seek: (offset: number) => Promise<void>
}

export async function installFakeSaveFilePicker(page: Page, opts?: { abort?: boolean }): Promise<void> {
  const shouldAbort = !!opts?.abort
  await page.addInitScript(
    ({ shouldAbort }) => {
      ;(window as any).__pw_savePickerCalls = []
      const store: Record<string, { bytes: Uint8Array; cursor: number }> = {}
      let nextId = 1

      function ensureHandleState(id: string) {
        store[id] ??= { bytes: new Uint8Array(0), cursor: 0 }
        return store[id]
      }

      ;(window as any).showSaveFilePicker = async (options: any = {}) => {
        if (shouldAbort) {
          const err: any = new Error('Aborted')
          err.name = 'AbortError'
          throw err
        }

        try {
          ;(window as any).__pw_savePickerCalls.push(options)
        } catch {
          // ignore
        }

        const id = String(nextId++)

        const handle: any = {
          __fake: 'FileSystemFileHandle',
          __id: id,
          queryPermission: async () => 'granted',
          requestPermission: async () => 'granted',
          getFile: async () => {
            const st = ensureHandleState(id)
            return new File([st.bytes], 'download.bin')
          },
          createWritable: async ({ keepExistingData }: any = {}) => {
            const st = ensureHandleState(id)
            if (!keepExistingData) {
              st.bytes = new Uint8Array(0)
              st.cursor = 0
            }

            const writable: FakeWritable = {
              write: async (chunk) => {
                const src = chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk as any)
                const needed = st.cursor + src.byteLength
                if (needed > st.bytes.byteLength) {
                  const grown = new Uint8Array(needed)
                  grown.set(st.bytes, 0)
                  st.bytes = grown
                }
                st.bytes.set(src, st.cursor)
                st.cursor += src.byteLength
              },
              seek: async (offset) => {
                st.cursor = Math.max(0, offset | 0)
              },
              close: async () => {},
              abort: async () => {},
            }
            return writable
          },
        }
        return handle
      }
    },
    { shouldAbort }
  )
}
