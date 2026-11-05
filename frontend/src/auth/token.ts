/**
 * Token storage.
 *
 * localStorage is fine for a portfolio project, but know the trade-off:
 * any script that runs on this page can read it, so an XSS bug becomes a stolen
 * token. A production app usually keeps the access token in memory and puts the
 * refresh token in an HttpOnly, Secure, SameSite cookie that JS cannot read.
 *
 * The payload is decoded for display only (who am I?). It is NOT verification —
 * the server always verifies the signature.
 */

const TOKEN_KEY = 'cowrite.token'

export interface Session {
  token: string
  email: string | null
}

interface JwtPayload {
  sub?: string
  email?: string
  iat?: number
  exp?: number
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

function decodePayload(token: string): JwtPayload | null {
  const [, payload] = token.split('.')
  if (!payload) return null

  try {
    // base64url -> base64, then pad: atob() rejects some unpadded inputs
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    return JSON.parse(atob(padded)) as JwtPayload
  } catch {
    return null
  }
}

function isExpired(payload: JwtPayload | null): boolean {
  if (!payload?.exp) return false
  return payload.exp * 1000 <= Date.now()
}

/**
 * Reads the stored token once at startup and drops it if it has already expired,
 * so the UI does not show a logged-in screen that 401s on the first request.
 */
export function readSession(): Session | null {
  const token = getToken()
  if (!token) return null

  const payload = decodePayload(token)
  if (isExpired(payload)) {
    clearToken()
    return null
  }

  return { token, email: payload?.email ?? null }
}
