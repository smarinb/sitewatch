export type SiteStatus = 'UNKNOWN' | 'UP' | 'DOWN'

export interface Site {
  id: number
  name: string
  url: string
  checkIntervalMinutes: number
  active: boolean
  status: SiteStatus
  lastCheckedAt: string | null
  createdAt: string
}

/** Body of POST and PUT /api/sites. PUT replaces the site, so send every field. */
export interface SiteInput {
  name: string
  url: string
  checkIntervalMinutes: number
  active: boolean
}

export interface Check {
  checkedAt: string
  up: boolean
  httpStatus: number | null
  responseTimeMs: number | null
  sslExpiresAt: string | null
  wpVersion: string | null
  error: string | null
}

export interface LatestCheck extends Check {
  siteId: number
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
}

export interface Credentials {
  email: string
  password: string
}

/** What the login page may offer, from GET /api/auth/config. */
export interface AuthConfig {
  registrationOpen: boolean
  demoAvailable: boolean
}

export interface NotificationSettings {
  emailAlerts: boolean
  telegramChatId: string | null
}

/** What GET and PUT /api/me/notifications answer: the settings plus whether the account address is confirmed. */
export interface NotificationStatus extends NotificationSettings {
  emailVerified: boolean
}

/** Per channel: "sent", "failed", "disabled", "email address not verified" or "not configured on the server". */
export type TestAlertResult = Record<string, string>
