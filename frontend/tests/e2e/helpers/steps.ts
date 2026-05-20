import { test as base } from '@playwright/test'

export async function given(title: string, body: () => Promise<void>): Promise<void> {
  await base.step(`Given ${title}`, body)
}

export async function when(title: string, body: () => Promise<void>): Promise<void> {
  await base.step(`When ${title}`, body)
}

export async function then(title: string, body: () => Promise<void>): Promise<void> {
  await base.step(`Then ${title}`, body)
}

