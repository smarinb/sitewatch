import { describe, expect, it, vi } from 'vitest'
import { bodiesSentTo, mockApi } from '../test/mocks.ts'
import { ApiError, createApi } from './client.ts'

describe('api client', () => {
  it('sends the bearer token and JSON bodies', async () => {
    const mock = mockApi({ 'POST /api/sites': { status: 201, json: { id: 1 } } })
    const api = createApi(() => 'tok123', vi.fn())

    await api.createSite({ name: 'Shop', url: 'https://shop.example.com', checkIntervalMinutes: 5, active: true })

    const [, init] = mock.mock.calls[0]
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer tok123')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(bodiesSentTo(mock, 'POST /api/sites')).toEqual([
      { name: 'Shop', url: 'https://shop.example.com', checkIntervalMinutes: 5, active: true },
    ])
  })

  it('sends no token when signed out', async () => {
    const mock = mockApi({ 'POST /api/auth/login': { json: { accessToken: 'x' } } })

    await createApi(() => null, vi.fn()).login({ email: 'a@b.c', password: 'secret123' })

    expect(new Headers(mock.mock.calls[0][1]?.headers).has('Authorization')).toBe(false)
  })

  it('puts optional query parameters in the URL and drops undefined ones', async () => {
    const mock = mockApi({ 'GET /api/sites/7/checks': { json: [] } })

    await createApi(() => 't', vi.fn()).history(7, { limit: 500, since: undefined })

    expect(new URL(String(mock.mock.calls[0][0])).search).toBe('?limit=500')
  })

  it('turns problem details into an ApiError with field errors', async () => {
    mockApi({
      'POST /api/auth/register': {
        status: 400,
        json: { detail: 'Validation failed', errors: { email: 'must be a well-formed email address' } },
      },
    })

    const error = await createApi(() => null, vi.fn())
      .register({ email: 'nope', password: 'secret123' })
      .catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 400,
      message: 'Validation failed',
      fieldErrors: { email: 'must be a well-formed email address' },
    })
  })

  it('signs the user out on a 401 for an authenticated request', async () => {
    mockApi({ 'GET /api/sites': { status: 401, json: { detail: 'Unauthorized' } } })
    const onUnauthorized = vi.fn()

    await expect(createApi(() => 'expired', onUnauthorized).listSites()).rejects.toMatchObject({ status: 401 })

    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat a failed login as an expired session', async () => {
    mockApi({ 'POST /api/auth/login': { status: 401, json: { detail: 'Invalid email or password' } } })
    const onUnauthorized = vi.fn()

    await expect(createApi(() => null, onUnauthorized).login({ email: 'a@b.c', password: 'x' })).rejects.toMatchObject({
      message: 'Invalid email or password',
    })

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('reports an unreachable server with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(createApi(() => 't', vi.fn()).listSites()).rejects.toMatchObject({ status: 0 })
  })

  it('handles empty 204 responses', async () => {
    mockApi({ 'DELETE /api/sites/3': { status: 204 } })

    await expect(createApi(() => 't', vi.fn()).deleteSite(3)).resolves.toBeUndefined()
  })
})
