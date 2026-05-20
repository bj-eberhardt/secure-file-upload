import { test, expect } from './fixtures/test'
import { makeFile } from './helpers/files'
import { given, when, then } from './helpers/steps'

test.describe('Upload (resume)', () => {
  test('pause during upload and resume completes successfully', async ({ uploadPage, page }) => {
    await given('chunk uploads are slowed down a bit', async () => {
      await page.route(/\/api\/v1\/uploads\/.+\/chunks\/\d+$/, async (route) => {
        if (route.request().method() === 'PUT') {
          await new Promise((r) => setTimeout(r, 75))
        }
        await route.continue()
      })
    })

    await given('the upload page is open with a multi-chunk file selected', async () => {
      await uploadPage.goto()
      await uploadPage.setFiles([makeFile('big.bin', 2 * 1024 * 1024)])
    })

    await when('upload is started', async () => {
      await uploadPage.startUpload()
      await expect(uploadPage.pauseButton).toBeVisible()
    })

    await when('the upload is paused', async () => {
      await uploadPage.pause()
    })

    await then('resume is available and upload start is not shown anymore', async () => {
      expect.soft(await uploadPage.uploadButton.isVisible().catch(() => false)).toBe(false)
      await expect(uploadPage.resumeButton).toBeVisible()
    })

    await when('upload is resumed', async () => {
      await uploadPage.resume()
    })

    await then('the share link is displayed', async () => {
      await expect(uploadPage.shareLinkInput).toBeVisible({ timeout: 60_000 })
    })
  })

  test('resume with a different file shows an error notice', async ({ uploadPage, page }) => {
    await given('an upload has been started and paused (resume info exists)', async () => {
      await uploadPage.goto()
      await uploadPage.setFiles([makeFile('first.bin', 2 * 1024 * 1024)])
      await uploadPage.startUpload()
      await expect(uploadPage.pauseButton).toBeVisible()
      await uploadPage.pause()
      await expect(uploadPage.resumeButton).toBeVisible()
    })

    await given('the page is reloaded and a different file is selected', async () => {
      await page.reload()
      await expect(uploadPage.root).toBeVisible()
      await uploadPage.setFiles([makeFile('different.bin', 2 * 1024 * 1024)])
      await expect(uploadPage.resumeButton).toBeVisible()
    })

    await when('resume is attempted', async () => {
      await uploadPage.resume()
    })

    await then('the notice bar is in error state', async () => {
      await expect(uploadPage.notice).toHaveAttribute('data-variant', 'error')
    })
  })
})

