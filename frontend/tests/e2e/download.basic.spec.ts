import { test, expect } from './fixtures/test'
import { makeFile } from './helpers/files'
import { parseShareLink } from './helpers/shareLink'
import { disableSaveFilePicker, installFakeSaveFilePicker } from './helpers/filePickerStub'
import { given, when, then } from './helpers/steps'
import { createCompletedUpload } from './helpers/flows'

test.describe('Download (basic)', () => {
  test('download page shows manifest metadata before download', async ({ uploadPage, downloadPage }) => {
    let shareUrl = ''
    await given('a completed upload exists', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('hello.txt', 128 * 1024, 'text/plain')])
    })

    await given('the download page is opened', async () => {
      await downloadPage.goto(shareUrl)
    })

    await then('the file list is shown', async () => {
      await expect(downloadPage.fileList).toBeVisible()
      await expect(downloadPage.fileRows).toHaveCount(1)
    })
  })

  test('single file download suggests the original filename (file picker path)', async ({ uploadPage, downloadPage, page }) => {
    let shareUrl = ''
    let uploadId = ''

    await given('a fake save file picker is installed (no OS dialogs, deterministic)', async () => {
      await installFakeSaveFilePicker(page)
    })

    await given('a completed upload exists', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('hello.txt', 128 * 1024, 'text/plain')])
      uploadId = parseShareLink(shareUrl).uploadId
    })

    await given('the download page is opened', async () => {
      await downloadPage.goto(shareUrl)
      await expect(downloadPage.fileList).toBeVisible()
    })

    await when('download is started', async () => {
      await downloadPage.startButton.click()
    })

    await then('the picker is called with the original filename and download completes', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'success', { timeout: 60_000 })
      const suggested = await page.evaluate(() => {
        const calls = (window as any).__pw_savePickerCalls as any[] | undefined
        return calls?.[0]?.suggestedName ?? null
      })
      expect.soft(suggested).toBeTruthy()
      expect(suggested).toBe('hello.txt')
      expect(uploadId).toBeTruthy()
    })
  })

  test('multi file download suggests a zip filename (file picker path)', async ({ uploadPage, downloadPage, page }) => {
    let shareUrl = ''
    let uploadId = ''

    await given('a fake save file picker is installed (no OS dialogs, deterministic)', async () => {
      await installFakeSaveFilePicker(page)
    })

    await given('a completed multi-file upload exists', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('a.bin', 8 * 1024), makeFile('b.bin', 12 * 1024)])
      uploadId = parseShareLink(shareUrl).uploadId
    })

    await given('the download page is opened', async () => {
      await downloadPage.goto(shareUrl)
      await expect(downloadPage.fileList).toBeVisible()
      await expect(downloadPage.fileRows).toHaveCount(2)
    })

    await when('download is started', async () => {
      await downloadPage.startButton.click()
    })

    await then('the picker is called with the zip filename and download completes', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'success', { timeout: 60_000 })
      const suggested = await page.evaluate(() => {
        const calls = (window as any).__pw_savePickerCalls as any[] | undefined
        return calls?.[0]?.suggestedName ?? null
      })
      expect.soft(suggested).toBeTruthy()
      expect(suggested).toBe(`secure-file-upload-${uploadId}.zip`)
    })
  })

  test('anchor fallback triggers a browser download (filename may be browser-generated)', async ({ uploadPage, downloadPage, page }) => {
    let shareUrl = ''

    await given('save file picker is disabled (force anchor fallback)', async () => {
      await disableSaveFilePicker(page)
    })

    await given('a completed upload exists', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('hello.txt', 64 * 1024, 'text/plain')])
      parseShareLink(shareUrl)
    })

    await given('the download page is opened', async () => {
      await downloadPage.goto(shareUrl)
      await expect(downloadPage.fileList).toBeVisible()
    })

    await when('download is started', async () => {
      await downloadPage.startButton.click()
    })

    await then('a browser download is triggered', async () => {
      const dl = await page.waitForEvent('download', { timeout: 30_000 })
      expect(dl.suggestedFilename()).toBeTruthy()
    })
  })
})
