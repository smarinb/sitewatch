import { createContext, useContext } from 'react'
import type { Api } from '../api/client.ts'
import type { Credentials } from '../api/types.ts'

export interface AuthState {
  /** Signed-in user's email, or null when signed out. */
  email: string | null
  /** True for the demo account: it can look but not change anything. */
  readOnly: boolean
  /** API client that sends the current token and signs the user out on a 401. */
  api: Api
  login(credentials: Credentials): Promise<void>
  register(credentials: Credentials): Promise<void>
  loginDemo(): Promise<void>
  logout(): void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) throw new Error('useAuth must be used inside <AuthProvider>')
  return state
}
