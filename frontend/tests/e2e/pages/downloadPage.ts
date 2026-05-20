import { expect, type Locator, type Page } from '@playwright/test'

export class DownloadPage {
  readonly page: Page
  readonly root: Locator
  readonly notice: Locator
  readonly progress: Locator
  readonly progressDetail: Locator
  readonly fileList: Locator
  readonly fileRows: Locator
  readonly startButton: Locator
  readonly resumeButton: Locator
  readonly restartButton: Locator

  constructor(page: Page) {
    this.page = page
    this.root = page.getByTestId('download-page')
    this.notice = page.getByTestId('download:notice')
    this.progress = page.getByTestId('download:progress')
    this.progressDetail = page.getByTestId('download:progress-detail')
    this.fileList = page.getByTestId('download:file-list')
    this.fileRows = page.getByTestId('download:file-row')
    this.startButton = page.getByTestId('download:btn-start')
    this.resumeButton = page.getByTestId('download:btn-resume')
    this.restartButton = page.getByTestId('download:btn-restart')
  }

  async goto(shareUrl: string): Promise<void> {
    await this.page.goto(shareUrl)
    await expect(this.root).toBeVisible()
  }
}

