import { expect } from '@playwright/test'
import type { FilePayload } from './files'
import { readShareLink } from './shareLink'

type UploadPageLike = {
  goto: () => Promise<void>
  setFiles: (files: FilePayload[]) => Promise<void>
  startUpload: () => Promise<void>
  shareLinkInput: { isVisible: (...args: any[]) => Promise<boolean> }
  page: any
}

export async function createCompletedUpload(uploadPage: UploadPageLike, files: FilePayload[]): Promise<string> {
  await uploadPage.goto()
  await uploadPage.setFiles(files)
  await uploadPage.startUpload()
  await expect(uploadPage.shareLinkInput as any).toBeVisible({ timeout: 60_000 })
  return await readShareLink(uploadPage.page)
}

