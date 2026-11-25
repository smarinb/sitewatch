import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { createApi } from '../api/client.ts'
import type { Credentials, TokenResponse } from '../api/types.ts'
import { AuthContext, type AuthState } from './AuthContext.ts'
import { tokenStore } from './tokenStore.ts'
import { clearSession, loadSession, parseSession, saveSession, type Session } from './session.ts'

/** setTimeout cannot wait longer than 2^31 - 1 ms (about 24.8 days). */
const MAX_TIMEOUT_MS = 2_147_483_647

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const stored = loadSession()
    tokenStore.set(stored?.token ?? null)
    return stored
  })

  const logout = useCallback(() => {
    tokenStore.set(null)
    clearSession()
    setSession(null)
  }, [])

  const api = useMemo(() => createApi(tokenStore.get, logout), [logout])

  const startSession = useCallback((response: TokenResponse) => {
    const next = parseSession(response.accessToken)
    if (!next) throw new Error('The server returned an invalid token')
    tokenStore.set(next.token)
    saveSession(next)
    setSession(next)
  }, [])

  const login = useCallback(
    async (credentials: Credentials) => startSession(await api.login(credentials)),
    [api, startSession],
  )
  const loginDemo = useCallback(async () => startSession(await api.loginDemo()), [api, startSession])
  const register = useCallback(
    async (credentials: Credentials) => startSession(await api.register(credentials)),
    [api, startSession],
  )

  // Sign out when the token expires, even if the tab stays open.
  useEffect(() => {
    if (!session) return
    const delay = Math.min(Math.max(session.expiresAt - Date.now(), 0), MAX_TIMEOUT_MS)
    const timer = setTimeout(logout, delay)
    return () => clearTimeout(timer)
  }, [session, logout])

  const value = useMemo<AuthState>(
    () => ({ email: session?.email ?? null, readOnly: session?.readOnly ?? false, api, login, register, loginDemo, logout }),
    [session, api, login, register, loginDemo, logout],
  )
  return <AuthContext value={value}>{children}</AuthContext>
}
