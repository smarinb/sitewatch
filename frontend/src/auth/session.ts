const STORAGE_KEY = 'sitewatch.token'

export interface Session {
  token: string
  email: string
  /** Epoch milliseconds. */
  expiresAt: number
}

/** Reads the claims we need from a JWT. The server still validates the signature; this is only for the UI. */
export function parseSession(token: string): Session | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: number; email?: string }
    if (typeof claims.exp !== 'number' || typeof claims.email !== 'string') return null
    return { token, email: claims.email, expiresAt: claims.exp * 1000 }
  } catch {
    return null
  }
}

/** The stored session, or null if there is none or it has expired. Storage may be blocked, so never throws. */
export function loadSession(now: number = Date.now()): Session | null {
  try {
    const token = localStorage.getItem(STORAGE_KEY)
    if (!token) return null
    const session = parseSession(token)
    if (!session || session.expiresAt <= now) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(session: Session): void {
  try {
    localStorage.setItem(STORAGE_KEY, session.token)
  } catch {
    // Private mode or blocked storage: the session simply lasts until the tab closes.
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to clear.
  }
}
