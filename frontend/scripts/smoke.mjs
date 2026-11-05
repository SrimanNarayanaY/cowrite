/**
 * Browser smoke test for Milestone 1.
 *
 * Requires the API (:8080) and the dev server (:5173) to be running:
 *   cd frontend && npm run dev
 *   npm run smoke
 *
 * Uses the locally installed Chrome via `channel: 'chrome'`, so no browser
 * download is needed. Start it with `channel: 'msedge'` if Chrome is missing.
 */
import { chromium } from 'playwright'

const BASE = process.env.FRONTEND_URL ?? 'http://localhost:5173'
const EMAIL = `e2e-${Date.now()}@example.com`
const PASSWORD = 'password123'
const TITLE = `E2E document ${Date.now()}`

const checks = []
function check(name, condition) {
  checks.push({ name, ok: Boolean(condition) })
  if (!condition) throw new Error(`FAILED: ${name}`)
  console.log(`  ✔ ${name}`)
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const page = await context.newPage()

// Auto-accept window.confirm() used by the delete button
page.on('dialog', (dialog) => dialog.accept())

try {
  console.log('1. Register a new user')
  await page.goto(`${BASE}/register`)
  await page.fill('input[name="email"]', EMAIL)
  await page.fill('input[name="password"]', PASSWORD)
  await page.fill('input[name="confirmPassword"]', PASSWORD)
  await page.click('button[type="submit"]')
  await page.waitForURL('**/docs')
  check('lands on /docs after register', page.url().endsWith('/docs'))
  check('email is shown in the top bar', await page.getByText(EMAIL).first().isVisible())

  console.log('2. Session survives a reload')
  await page.reload()
  await page.waitForURL('**/docs')
  check('still on /docs after reload', page.url().endsWith('/docs'))

  console.log('3. Create a document')
  await page.getByRole('button', { name: 'New document' }).click()
  await page.fill('input[placeholder="Design notes"]', TITLE)
  await page.fill('textarea', 'Created by the Playwright smoke test.')
  await page.getByRole('button', { name: 'Create document' }).click()
  await page.getByText(TITLE).waitFor()
  check('new document card is visible', await page.getByText(TITLE).isVisible())

  console.log('4. Empty-state onboarding is gone')
  check('empty state is not shown', (await page.getByText('No documents yet').count()) === 0)

  console.log('5. A different user cannot see the document')
  const otherContext = await browser.newContext()
  const otherPage = await otherContext.newPage()
  await otherPage.goto(`${BASE}/register`)
  await otherPage.fill('input[name="email"]', `e2e-other-${Date.now()}@example.com`)
  await otherPage.fill('input[name="password"]', PASSWORD)
  await otherPage.fill('input[name="confirmPassword"]', PASSWORD)
  await otherPage.click('button[type="submit"]')
  await otherPage.waitForURL('**/docs')
  await otherPage.getByText('No documents yet').waitFor()
  check('second user sees the empty state', await otherPage.getByText('No documents yet').isVisible())
  check('second user cannot see the first user document', (await otherPage.getByText(TITLE).count()) === 0)
  await otherContext.close()

  console.log('6. Delete the document')
  await page
    .locator('.doc-card', { hasText: TITLE })
    .getByRole('button', { name: 'Delete' })
    .click()
  await page.getByText(TITLE).waitFor({ state: 'detached' })
  check('document card is removed', (await page.getByText(TITLE).count()) === 0)

  console.log('7. Log out')
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.waitForURL('**/login')
  check('logout lands on /login', page.url().endsWith('/login'))

  console.log('8. Protected route without a token')
  await page.goto(`${BASE}/docs`)
  await page.waitForURL('**/login')
  check('/docs redirects to /login', page.url().endsWith('/login'))

  console.log(`\nPASS — ${checks.length} checks`)
} finally {
  await browser.close()
}
