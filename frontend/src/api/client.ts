import type {
  Check,
  Credentials,
  LatestCheck,
  NotificationSettings,
  NotificationStatus,
  Site,
  SiteInput,
  TestAlertResult,
  TokenResponse,
} from './types.ts'

/** Empty in development (Vite proxies /api); the API origin in production builds. */
const BASE_URL: string = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')

/** An error answered by the API (RFC 9457 problem details) or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number
  /** Field name to message, for validation errors. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined>
}

export interface Api {
  register(credentials: Credentials): Promise<TokenResponse>
  login(credentials: Credentials): Promise<TokenResponse>
  listSites(): Promise<Site[]>
  getSite(id: number): Promise<Site>
  createSite(input: SiteInput): Promise<Site>
  updateSite(id: number, input: SiteInput): Promise<Site>
  deleteSite(id: number): Promise<void>
  history(id: number, params: { limit?: number; since?: string }): Promise<Check[]>
  latestChecks(): Promise<LatestCheck[]>
  getNotifications(): Promise<NotificationStatus>
  saveNotifications(settings: NotificationSettings): Promise<NotificationStatus>
  sendTestAlert(): Promise<TestAlertResult>
  /** Confirms the address with the token from the email link. Public: needs no session. */
  verifyEmail(token: string): Promise<void>
  /** Emails a new confirmation link to the signed-in user. */
  resendVerification(): Promise<void>
}

/**
 * Builds the API client. {@code getToken} is read on every request, and
 * {@code onUnauthorized} fires when an authenticated request is answered 401
 * (expired or revoked token), so the app can send the user back to the login.
 */
export function createApi(getToken: () => string | null, onUnauthorized: () => void): Api {
  async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const url = new URL(BASE_URL + path, window.location.origin)
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined) url.searchParams.set(key, String(value))
    }
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
    if (options.body !== undefined) headers['Content-Type'] = 'application/json'

    let response: Response
    try {
      response = await fetch(url, {
        method: options.method ?? 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      })
    } catch {
      throw new ApiError(0, 'Could not reach the server. Check your connection and try again.')
    }

    if (response.ok) {
      return (response.status === 204 ? undefined : await response.json()) as T
    }
    const problem = await response.json().catch(() => null)
    if (response.status === 401 && token) onUnauthorized()
    throw new ApiError(
      response.status,
      typeof problem?.detail === 'string' ? problem.detail : `Request failed (${response.status})`,
      problem?.errors && typeof problem.errors === 'object' ? problem.errors : {},
    )
  }

  return {
    register: (credentials) => request('/api/auth/register', { method: 'POST', body: credentials }),
    login: (credentials) => request('/api/auth/login', { method: 'POST', body: credentials }),
    listSites: () => request('/api/sites'),
    getSite: (id) => request(`/api/sites/${id}`),
    createSite: (input) => request('/api/sites', { method: 'POST', body: input }),
    updateSite: (id, input) => request(`/api/sites/${id}`, { method: 'PUT', body: input }),
    deleteSite: (id) => request(`/api/sites/${id}`, { method: 'DELETE' }),
    history: (id, params) => request(`/api/sites/${id}/checks`, { query: params }),
    latestChecks: () => request('/api/checks/latest'),
    getNotifications: () => request('/api/me/notifications'),
    saveNotifications: (settings) => request('/api/me/notifications', { method: 'PUT', body: settings }),
    sendTestAlert: () => request('/api/me/notifications/test', { method: 'POST' }),
    verifyEmail: (token) => request('/api/auth/verify-email', { method: 'POST', body: { token } }),
    resendVerification: () => request('/api/me/email-verification', { method: 'POST' }),
  }
}
