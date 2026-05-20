import { test, expect } from './fixtures/test'
import { given, then } from './helpers/steps'

test('smoke: upload page renders core controls', async ({ uploadPage }) => {
  await given('the app is opened', async () => {
    await uploadPage.goto()
  })

  await then('the upload page is visible', async () => {
    expect.soft(await uploadPage.root.isVisible()).toBe(true)
    expect.soft(await uploadPage.fileInput.isVisible()).toBe(true)
    await expect(uploadPage.uploadButton).toBeDisabled()
  })
})

test('smoke: download route renders via backend SPA fallback', async ({ downloadPage }) => {
  await given('a deep-link is opened', async () => {
    await downloadPage.goto('/d/someId#key=abc')
  })

  await then('the download page is visible', async () => {
    await expect(downloadPage.root).toBeVisible()
  })
})
