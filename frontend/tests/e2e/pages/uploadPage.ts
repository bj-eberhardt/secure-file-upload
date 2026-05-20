import { expect, type Locator, type Page } from '@playwright/test'
import type { FilePayload } from '../helpers/files'

export class UploadPage {
  readonly page: Page
  readonly root: Locator
  readonly notice: Locator
  readonly fileInput: Locator
  readonly fileList: Locator
  readonly fileRows: Locator
  readonly uploadButton: Locator
  readonly resumeButton: Locator
  readonly pauseButton: Locator
  readonly newUploadButton: Locator
  readonly progress: Locator
  readonly progressDetail: Locator
  readonly shareLinkInput: Locator
  readonly copyLinkButton: Locator
  readonly moreFilesButton: Locator

  constructor(page: Page) {
    this.page = page
    this.root = page.getByTestId('upload-page')
    this.notice = page.getByTestId('upload:notice')
    this.fileInput = page.getByTestId('upload:file-input')
    this.fileList = page.getByTestId('upload:file-list')
    this.fileRows = page.getByTestId('upload:file-row')
    this.uploadButton = page.getByTestId('upload:btn-upload')
    this.resumeButton = page.getByTestId('upload:btn-resume')
    this.pauseButton = page.getByTestId('upload:btn-pause')
    this.newUploadButton = page.getByTestId('upload:btn-new')
    this.progress = page.getByTestId('upload:progress')
    this.progressDetail = page.getByTestId('upload:progress-detail')
    this.shareLinkInput = page.getByTestId('upload:share-link')
    this.copyLinkButton = page.getByTestId('upload:btn-copy-link')
    this.moreFilesButton = page.getByTestId('upload:btn-more-files')
  }

  async goto(): Promise<void> {
    await this.page.goto('/')
    await expect(this.root).toBeVisible()
  }

  async setFiles(files: FilePayload[]): Promise<void> {
    await this.fileInput.setInputFiles(files)
  }

  async startUpload(): Promise<void> {
    await this.uploadButton.click()
  }

  async pause(): Promise<void> {
    await this.pauseButton.click()
  }

  async resume(): Promise<void> {
    await this.resumeButton.click()
  }

  async startNewUpload(): Promise<void> {
    await this.newUploadButton.click()
  }
}

