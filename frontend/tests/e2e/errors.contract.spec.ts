import { test, expect } from './fixtures/test'
import { mockJson, mockStatus } from './helpers/mocks'
import { makeFile } from './helpers/files'
import { disableSaveFilePicker, installFakeSaveFilePicker } from './helpers/filePickerStub'
import { given, when, then } from './helpers/steps'
import { createCompletedUpload } from './helpers/flows'
import { parseShareLink } from './helpers/shareLink'

test.describe('Error contract (mocked)', () => {
  test('init 429 shows an error notice', async ({ uploadPage, page }) => {
    await given('init is rate limited', async () => {
      await mockJson(
        page,
        /\/api\/v1\/uploads\/init$/,
        429,
        { message: 'Too Many Requests' },
        { 'Retry-After': '1' }
      )
    })

    await given('the upload page is open with a file selected', async () => {
      await uploadPage.goto()
      await uploadPage.setFiles([makeFile('a.bin', 8 * 1024)])
    })

    await when('upload is started', async () => {
      await uploadPage.startUpload()
    })

    await then('the notice bar is in error state', async () => {
      await expect(uploadPage.notice).toHaveAttribute('data-variant', 'error')
    })
  })

  test('download 410 shows an error notice', async ({ downloadPage, page }) => {
    await given('save file picker is disabled (so chunks are fetched via XHR)', async () => {
      await disableSaveFilePicker(page)
    })

    await given('status is completed and chunk downloads return 410', async () => {
      await mockJson(page, /\/api\/v1\/uploads\/someId\/status$/, 200, {
        uploadId: 'someId',
        completed: true,
        uploadedChunks: [0],
        protocolVersion: 'v1',
        chunkSize: 262144,
        chunkCount: 1,
      })
      await mockStatus(page, /\/api\/v1\/uploads\/someId\/chunks\/0$/, 410)
    })

    await given('the download page is open', async () => {
      await downloadPage.goto('/d/someId#key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')
    })

    await when('download is started', async () => {
      if (await downloadPage.startButton.isVisible()) await downloadPage.startButton.click()
    })

    await then('the notice bar is in error state', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'error')
    })
  })

  test('download 429 respects Retry-After and still succeeds (real flow + route)', async ({ uploadPage, downloadPage, page }) => {
    await given('save file picker is disabled (so chunks are fetched via XHR)', async () => {
      await disableSaveFilePicker(page)
    })

    let shareUrl = ''
    let uploadId = ''
    await given('a completed upload exists', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('hello.txt', 128 * 1024, 'text/plain')])
      uploadId = parseShareLink(shareUrl).uploadId
    })

    await given('chunk 0 is rate limited once', async () => {
      let used = false
      await page.route(new RegExp(`/api/v1/uploads/${uploadId}/chunks/0$`), async (route) => {
        if (!used) {
          used = true
          await route.fulfill({ status: 429, body: '', headers: { 'Retry-After': '1' } })
          return
        }
        await route.fallback()
      })
    })

    await given('the download page is open', async () => {
      await downloadPage.goto(shareUrl)
      await expect(downloadPage.fileList).toBeVisible()
    })

    await when('download is started', async () => {
      await downloadPage.startButton.click()
    })

    await then('the download succeeds (no permanent failure on 429)', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'success', { timeout: 60_000 })
    })
  })

  test('save dialog abort shows a german error message', async ({ downloadPage, page }) => {
    await given('save file picker aborts', async () => {
      await installFakeSaveFilePicker(page, { abort: true })
    })

    await given('the download page is open', async () => {
      await downloadPage.goto('/d/someId#key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')
    })

    await when('download is started', async () => {
      if (await downloadPage.startButton.isVisible()) await downloadPage.startButton.click()
    })

    await then('the notice bar is in error state', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'error')
      await expect(downloadPage.notice).toContainText('Speichern abgebrochen')
    })
  })
})
