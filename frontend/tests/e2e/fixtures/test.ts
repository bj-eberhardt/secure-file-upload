import { test as base, expect } from '@playwright/test'
import { UploadPage } from '../pages/uploadPage'
import { DownloadPage } from '../pages/downloadPage'

export type E2EFixtures = {
  uploadPage: UploadPage
  downloadPage: DownloadPage
}

export const test = base.extend<E2EFixtures>({
  uploadPage: async ({ page }, use) => {
    await use(new UploadPage(page))
  },
  downloadPage: async ({ page }, use) => {
    await use(new DownloadPage(page))
  },
})

export { expect }
