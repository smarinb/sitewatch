import { describe, expect, it } from 'vitest'
import { daysUntil, formatMs, hostname, relativeTime } from './format.ts'
import { average, formatUptime, percentile, responseTimes, sslState, uptimePercent } from './stats.ts'
import { normalizeUrl } from './url.ts'
import { check } from '../test/mocks.ts'

const NOW = Date.parse('2026-11-24T12:00:00Z')
const ago = (ms: number) => new Date(NOW - ms).toISOString()

describe('format', () => {
  it('formats milliseconds and seconds', () => {
    expect(formatMs(312)).toBe('312 ms')
    expect(formatMs(1500)).toBe('1.50 s')
    expect(formatMs(12_300)).toBe('12.3 s')
  })

  it('describes elapsed time', () => {
    expect(relativeTime(ago(20_000), NOW)).toBe('just now')
    expect(relativeTime(ago(5 * 60_000), NOW)).toBe('5 min ago')
    expect(relativeTime(ago(3 * 3_600_000), NOW)).toBe('3 h ago')
    expect(relativeTime(ago(2 * 86_400_000), NOW)).toBe('2 d ago')
    expect(relativeTime(new Date(NOW + 60_000).toISOString(), NOW)).toBe('just now')
  })

  it('counts whole days until a date, negative once past', () => {
    expect(daysUntil(new Date(NOW + 10.5 * 86_400_000).toISOString(), NOW)).toBe(10)
    expect(daysUntil(new Date(NOW - 3_600_000).toISOString(), NOW)).toBe(-1)
  })

  it('extracts the host and tolerates garbage', () => {
    expect(hostname('https://shop.example.com:8443/path')).toBe('shop.example.com:8443')
    expect(hostname('not a url')).toBe('not a url')
  })
})

describe('stats', () => {
  it('computes uptime over checks', () => {
    expect(uptimePercent([])).toBeNull()
    expect(uptimePercent([check(0), check(5), check(10, { up: false }), check(15)])).toBe(75)
  })

  it('never rounds a near-perfect uptime up to 100%', () => {
    expect(formatUptime(100)).toBe('100%')
    expect(formatUptime(99.99)).toBe('99.9%')
    expect(formatUptime(66.66)).toBe('66.6%')
    expect(formatUptime(0)).toBe('0.0%')
  })

  it('ignores failed checks for response times', () => {
    const times = responseTimes([check(0, { responseTimeMs: 100 }), check(5, { up: false, responseTimeMs: null })])
    expect(times).toEqual([100])
    expect(average([100, 300])).toBe(200)
    expect(average([])).toBeNull()
  })

  it('uses nearest-rank percentiles', () => {
    const values = Array.from({ length: 20 }, (_, i) => (i + 1) * 10)
    expect(percentile(values, 95)).toBe(190)
    expect(percentile(values, 100)).toBe(200)
    expect(percentile([5], 95)).toBe(5)
    expect(percentile([], 95)).toBeNull()
  })

  it('flags certificates the way the server alerts', () => {
    expect(sslState(60)).toBe('ok')
    expect(sslState(14)).toBe('ok')
    expect(sslState(13)).toBe('expiring')
    expect(sslState(0)).toBe('expiring')
    expect(sslState(-1)).toBe('expired')
  })
})

describe('normalizeUrl', () => {
  it('adds https to bare hosts and leaves real schemes alone', () => {
    expect(normalizeUrl('  example.com/blog ')).toBe('https://example.com/blog')
    expect(normalizeUrl('http://example.com')).toBe('http://example.com')
    expect(normalizeUrl('HTTPS://Example.com')).toBe('HTTPS://Example.com')
    expect(normalizeUrl('ftp://example.com')).toBe('ftp://example.com')
    expect(normalizeUrl('')).toBe('')
  })
})
