import { describe, expect, it } from 'vitest'
import { fakeJwt } from '../test/mocks.ts'
import { loadSession, parseSession, saveSession } from './session.ts'

describe('session', () => {
  it('reads email and expiry from the token', () => {
    const session = parseSession(fakeJwt('ana@example.com', 600))

    expect(session?.email).toBe('ana@example.com')
    expect(session!.expiresAt).toBeGreaterThan(Date.now())
    expect(session!.expiresAt).toBeLessThanOrEqual(Date.now() + 600_000)
  })

  it.each(['', 'garbage', 'a.b.c', 'a.%%%.c'])('rejects a malformed token (%j)', (token) => {
    expect(parseSession(token)).toBeNull()
  })

  it('restores a stored session', () => {
    const session = parseSession(fakeJwt('ana@example.com'))!
    saveSession(session)

    expect(loadSession()?.email).toBe('ana@example.com')
  })

  it('drops an expired session instead of restoring it', () => {
    saveSession(parseSession(fakeJwt('ana@example.com', -10))!)

    expect(loadSession()).toBeNull()
    expect(localStorage.getItem('sitewatch.token')).toBeNull()
  })
})
