import { test, expect } from './fixtures/test'
import { makeFile } from './helpers/files'
import { given, when, then } from './helpers/steps'

test.describe('Upload (basic)', () => {
  test('single file upload shows share link', async ({ uploadPage }) => {
    await given('the upload page is open', async () => {
      await uploadPage.goto()
    })

    await when('a single file is selected', async () => {
      await uploadPage.setFiles([makeFile('hello.txt', 64 * 1024, 'text/plain')])
    })

    await then('the file list shows one entry', async () => {
      await expect(uploadPage.fileList).toBeVisible()
      await expect(uploadPage.fileRows).toHaveCount(1)
    })

    await when('encrypted upload is started', async () => {
      await uploadPage.startUpload()
    })

    await then('a share link is displayed', async () => {
      await expect(uploadPage.notice).toHaveAttribute('data-variant', 'success', { timeout: 60_000 })
      await expect(uploadPage.shareLinkInput).toBeVisible()
      await expect(uploadPage.shareLinkInput).toHaveValue(/\/d\/[^#]+#key=.+/)
    })
  })

  test('multi file upload shows share link', async ({ uploadPage }) => {
    await given('the upload page is open', async () => {
      await uploadPage.goto()
    })

    await when('two files are selected', async () => {
      await uploadPage.setFiles([makeFile('a.bin', 8 * 1024), makeFile('b.bin', 12 * 1024)])
    })

    await then('the file list shows multiple entries', async () => {
      await expect(uploadPage.fileList).toBeVisible()
      await expect(uploadPage.fileRows).toHaveCount(2)
    })

    await when('encrypted upload is started', async () => {
      await uploadPage.startUpload()
    })

    await then('a share link is displayed', async () => {
      await expect(uploadPage.shareLinkInput).toBeVisible()
      await expect(uploadPage.shareLinkInput).toHaveValue(/\/d\/[^#]+#key=.+/)
    })
  })
})
