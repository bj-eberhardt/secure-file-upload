import { test, expect } from './fixtures/test'
import { makeFile } from './helpers/files'
import { parseShareLink } from './helpers/shareLink'
import { given, when, then } from './helpers/steps'
import { installFakeSaveFilePicker } from './helpers/filePickerStub'
import { mockOnce } from './helpers/mocks'
import { createCompletedUpload } from './helpers/flows'

async function readResumeNextChunkIndexFromUi(notice: any): Promise<number | null> {
  const attr = await notice.getAttribute('data-resume-next-chunk')
  if (!attr) return null
  const n = Number.parseInt(attr, 10)
  return Number.isFinite(n) ? n : null
}

test.describe('Download (resume)', () => {
  test('chunk failure offers resume and continues from next chunk', async ({ uploadPage, downloadPage, page }) => {
    await given('a fake save file picker is installed (no OS dialogs)', async () => {
      await installFakeSaveFilePicker(page)
    })

    let shareUrl = ''
    let uploadId = ''
    await given('a completed upload exists with multiple chunks', async () => {
      shareUrl = await createCompletedUpload(uploadPage, [makeFile('big.bin', 2 * 1024 * 1024)])
      uploadId = parseShareLink(shareUrl).uploadId
    })

    await given('the download page is open', async () => {
      await downloadPage.goto(shareUrl)
      await expect(downloadPage.fileList).toBeVisible()
    })

    await given('chunk 1 will fail once', async () => {
      await mockOnce(page, new RegExp(`/api/v1/uploads/${uploadId}/chunks/1$`), async (route) => {
        await route.abort()
      })
    })

    await when('download is started', async () => {
      await downloadPage.startButton.click()
    })

    await then('the UI shows an error and resume becomes available', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'error', { timeout: 60_000 })
      await expect(downloadPage.resumeButton).toBeVisible()
      const nextIdx = await readResumeNextChunkIndexFromUi(downloadPage.notice)
      expect(nextIdx).not.toBeNull()
      expect((nextIdx as number)!).toBeGreaterThan(0)
    })

    const nextBefore = await readResumeNextChunkIndexFromUi(downloadPage.notice)

    await when('download is resumed', async () => {
      await downloadPage.resumeButton.click()
    })

    await then('resume continues and clears the resume entry on success', async () => {
      await expect(downloadPage.notice).toHaveAttribute('data-variant', 'success', { timeout: 60_000 })
      const nextAfter = await readResumeNextChunkIndexFromUi(downloadPage.notice)
      expect.soft(nextBefore).not.toBeNull()
      expect(nextAfter).toBeNull()
    })
  })

  test('restart clears resume state', async ({ uploadPage, downloadPage, page }) => {
    await given('a fake save file picker is installed', async () => {
      await installFakeSaveFilePicker(page)
    })

    const shareUrl = await createCompletedUpload(uploadPage, [makeFile('big.bin', 2 * 1024 * 1024)])
    const { uploadId } = parseShareLink(shareUrl)
    await downloadPage.goto(shareUrl)

    await given('a failed first attempt created a resume entry', async () => {
      await mockOnce(page, new RegExp(`/api/v1/uploads/${uploadId}/chunks/1$`), async (route) => {
        await route.abort()
      })
      await downloadPage.startButton.click()
      await expect(downloadPage.resumeButton).toBeVisible({ timeout: 60_000 })
      const nextIdx = await readResumeNextChunkIndexFromUi(downloadPage.notice)
      expect(nextIdx).not.toBeNull()
      expect((nextIdx as number)!).toBeGreaterThan(0)
    })

    await when('restart is clicked', async () => {
      await downloadPage.restartButton.click()
    })

    await then('resume is no longer available', async () => {
      await expect(downloadPage.resumeButton).not.toBeVisible()
      const nextIdx = await readResumeNextChunkIndexFromUi(downloadPage.notice)
      expect(nextIdx).toBeNull()
    })
  })
})
