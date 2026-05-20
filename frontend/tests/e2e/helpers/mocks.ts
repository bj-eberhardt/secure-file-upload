import type { Page, Route } from '@playwright/test'

export async function mockOnce(
  page: Page,
  url: RegExp,
  handler: (route: Route) => Promise<void> | void
): Promise<void> {
  let used = false
  await page.route(url, async (route) => {
    if (used) return route.fallback()
    used = true
    await handler(route)
  })
}

export async function mockJson(
  page: Page,
  url: RegExp,
  status: number,
  json: unknown,
  headers?: Record<string, string>
): Promise<void> {
  await page.route(url, async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(json),
      headers,
    })
  })
}

export async function mockStatus(
  page: Page,
  url: RegExp,
  status: number,
  headers?: Record<string, string>
): Promise<void> {
  await page.route(url, async (route) => {
    await route.fulfill({ status, body: '', headers })
  })
}

