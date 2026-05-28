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

const reporters: any[] = [
  ['list'],
  ['html', { open: 'never' }],
  ['json', { outputFile: 'test-results/test-results.json' }],
]
if (isCi) reporters.push(['github'])

export default defineConfig({
  testDir: 'tests/e2e',
  workers: isCi ? 1 : 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: isCi ? 1 : 0,
  outputDir: 'test-results',
  reporter: reporters,
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
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
  ],
  webServer: {
    cwd: repoRoot,
    command:
      `${gradleCmd} :backend:run ` +
      `-Dmicronaut.environments=e2e ` +
      `-Dmicronaut.server.port=${port} ` +
      `-Dsecure-file-upload.rate-limit-enabled=false ` +
      `-Dsecure-file-upload.cleanup-enabled=false ` +
      `-Dsecure-file-upload.chunk-size=262144 ` +
      `-Dsecure-file-upload.min-chunk-bytes=262144 ` +
      `-Dsecure-file-upload.max-chunk-bytes=1048576 ` +
      `-Dsecure-file-upload.storage-dir=backend/build/e2e-uploads`,
    url: `http://localhost:${port}/health`,
    timeout: 180_000,
    reuseExistingServer: reuseExistingServer && !isCi,
  },
})
