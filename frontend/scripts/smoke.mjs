/**
 * Browser smoke test.
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
const API = process.env.API_URL ?? 'http://localhost:8080/api'
const EMAIL = `e2e-${Date.now()}@example.com`
const PASSWORD = 'password123'
const TITLE = `E2E document ${Date.now()}`
const TYPED_TEXT = 'Hello from the Tiptap editor.'

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

  console.log('5. Open the editor and auto-save')
  await page.locator('.doc-card', { hasText: TITLE }).getByRole('link', { name: 'Open' }).click()
  await page.waitForURL('**/docs/*')
  const editor = page.locator('.ProseMirror')
  await editor.waitFor()
  check('editor route is open', /\/docs\/\d+$/.test(page.url()))

  await editor.click()
  await page.keyboard.type(` ${TYPED_TEXT}`)
  await page.getByText('Saved', { exact: true }).waitFor({ timeout: 10_000 })
  check('debounced save reports Saved', await page.getByText('Saved', { exact: true }).isVisible())
  check('version advanced to v1', await page.getByText('v1', { exact: true }).isVisible())

  console.log('6. Saved content survives a reload')
  await page.reload()
  await page.locator('.ProseMirror').waitFor()
  check('typed text is still in the document', (await page.locator('.ProseMirror').innerText()).includes(TYPED_TEXT))

  console.log('7. A stale tab gets a 409 conflict')
  const docId = new URL(page.url()).pathname.split('/').pop()
  const stalePage = await context.newPage()
  await stalePage.goto(`${BASE}/docs/${docId}`)
  await stalePage.locator('.ProseMirror').waitFor()

  // Page A saves first, so page B still holds the previous version.
  await page.locator('.ProseMirror').click()
  await page.keyboard.type(' Edited in tab A.')
  await page.getByText('Saved', { exact: true }).waitFor({ timeout: 10_000 })

  await stalePage.locator('.ProseMirror').click()
  await stalePage.keyboard.type(' Edited in tab B.')
  await stalePage.getByText('Document changed elsewhere', { exact: false }).waitFor({ timeout: 10_000 })
  check('stale tab shows the conflict message', await stalePage.getByText('Document changed elsewhere').isVisible())
  check('conflict offers a Reload action', await stalePage.getByRole('button', { name: 'Reload' }).isVisible())
  await stalePage.close()

  console.log('8. A VIEWER can open the document but cannot type')
  const viewerEmail = `e2e-viewer-${Date.now()}@example.com`
  await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: viewerEmail, password: PASSWORD }),
  })
  const ownerToken = await page.evaluate(() => localStorage.getItem('cowrite.token'))
  const shareResponse = await fetch(`${API}/documents/${docId}/share`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${ownerToken}` },
    body: JSON.stringify({ email: viewerEmail, role: 'VIEWER' }),
  })
  check('owner shared the document', shareResponse.ok)

  const viewerContext = await browser.newContext()
  const viewerPage = await viewerContext.newPage()
  await viewerPage.goto(`${BASE}/login`)
  await viewerPage.fill('input[name="email"]', viewerEmail)
  await viewerPage.fill('input[name="password"]', PASSWORD)
  await viewerPage.click('button[type="submit"]')
  await viewerPage.waitForURL('**/docs')
  await viewerPage.goto(`${BASE}/docs/${docId}`)
  await viewerPage.locator('.ProseMirror').waitFor()

  check('viewer sees the editor content', (await viewerPage.locator('.ProseMirror').innerText()).includes(TYPED_TEXT))
  check('viewer editor is not editable', (await viewerPage.locator('.ProseMirror').getAttribute('contenteditable')) === 'false')
  check('viewer sees the read-only badge', await viewerPage.getByText('View only', { exact: true }).isVisible())
  check('viewer toolbar is disabled', await viewerPage.getByRole('button', { name: 'Bold' }).isDisabled())
  await viewerContext.close()

  console.log('9. Delete the document')
  await page.goto(`${BASE}/docs`)
  await page
    .locator('.doc-card', { hasText: TITLE })
    .getByRole('button', { name: 'Delete' })
    .click()
  await page.getByText(TITLE).waitFor({ state: 'detached' })
  check('document card is removed', (await page.getByText(TITLE).count()) === 0)

  console.log('10. Log out')
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.waitForURL('**/login')
  check('logout lands on /login', page.url().endsWith('/login'))

  console.log('11. Protected route without a token')
  await page.goto(`${BASE}/docs`)
  await page.waitForURL('**/login')
  check('/docs redirects to /login', page.url().endsWith('/login'))

  console.log(`\nPASS — ${checks.length} checks`)
} finally {
  await browser.close()
}
