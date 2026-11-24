import { vi } from 'vitest'
import type { Check, Site } from '../api/types.ts'

const base64Url = (value: unknown) =>
  btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')

/** An unsigned token with the claims the UI reads. The server, not the UI, validates signatures. */
export function fakeJwt(email = 'demo@example.com', ttlSeconds = 3600): string {
  const exp = Math.floor(Date.now() / 1000) + ttlSeconds
  return `${base64Url({ alg: 'HS256' })}.${base64Url({ sub: '1', email, exp })}.signature`
}

export function signIn(email = 'demo@example.com') {
  localStorage.setItem('sitewatch.token', fakeJwt(email))
}

interface MockRequest {
  url: URL
  body: unknown
  headers: Headers
}
type Reply = { status?: number; json?: unknown }
type Handler = Reply | ((request: MockRequest) => Reply)

/**
 * Replaces fetch with a router keyed by "METHOD /path". A request nobody mocked
 * fails loudly, so a test never silently talks to a real network.
 */
export function mockApi(routes: Record<string, Handler>) {
  const mock = vi.fn(async (input: URL | string, init?: RequestInit) => {
    const url = new URL(String(input))
    const method = init?.method ?? 'GET'
    const handler = routes[`${method} ${url.pathname}`]
    if (!handler) throw new Error(`Unmocked request: ${method} ${url.pathname}`)
    const request: MockRequest = {
      url,
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
      headers: new Headers(init?.headers),
    }
    const { status = 200, json } = typeof handler === 'function' ? handler(request) : handler
    return status === 204 || json === undefined
      ? new Response(null, { status })
      : new Response(JSON.stringify(json), { status, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

/** Calls made to one route, as parsed JSON bodies. */
export function bodiesSentTo(mock: ReturnType<typeof mockApi>, route: string): unknown[] {
  return mock.mock.calls
    .filter(([input, init]) => `${init?.method ?? 'GET'} ${new URL(String(input)).pathname}` === route)
    .map(([, init]) => (typeof init?.body === 'string' ? JSON.parse(init.body) : undefined))
}

export function site(overrides: Partial<Site> = {}): Site {
  return {
    id: 1,
    name: 'Shop',
    url: 'https://shop.example.com/',
    checkIntervalMinutes: 5,
    active: true,
    status: 'UP',
    lastCheckedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

export function check(minutesAgo: number, overrides: Partial<Check> = {}): Check {
  return {
    checkedAt: new Date(Date.now() - minutesAgo * 60_000).toISOString(),
    up: true,
    httpStatus: 200,
    responseTimeMs: 300,
    sslExpiresAt: null,
    wpVersion: null,
    error: null,
    ...overrides,
  }
}
