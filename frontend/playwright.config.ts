import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const repoRoot = path.resolve(__dirname, '..')
const isCi = !!process.env.CI
const isWindows = process.platform === 'win32'
const reuseExistingServer = process.env.PW_REUSE_SERVER === 'true'
const port = Number.parseInt(process.env.PW_PORT ?? '18080', 10) || 18080

const gradleCmd = isWindows ? 'gradlew.bat' : './gradlew'

export default defineConfig({
  testDir: 'tests/e2e',
  workers: isCi ? 1 : 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: isCi ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      // Default: Playwright-managed Chromium (installed via `:frontend:npmPlaywrightInstall`).
      // Optional override: set `PW_CHANNEL=chrome` (or `msedge`) to use a locally installed browser.
      use: { ...devices['Desktop Chrome'], channel: process.env.PW_CHANNEL || undefined },
    },
  ],
  webServer: {
    cwd: repoRoot,
    command:
      `${gradleCmd} :backend:run ` +
      `-Dmicronaut.environments=e2e ` +
      `-Dmicronaut.server.port=${port} ` +
      `-Dsecure-upload.rate-limit-enabled=false ` +
      `-Dsecure-upload.cleanup-enabled=false ` +
      `-Dsecure-upload.chunk-size=262144 ` +
      `-Dsecure-upload.min-chunk-bytes=262144 ` +
      `-Dsecure-upload.max-chunk-bytes=1048576 ` +
      `-Dsecure-upload.storage-dir=backend/build/e2e-uploads`,
    url: `http://localhost:${port}/health`,
    timeout: 180_000,
    reuseExistingServer: reuseExistingServer && !isCi,
  },
})
