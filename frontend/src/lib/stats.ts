import type { Check } from '../api/types.ts'

export type SslState = 'ok' | 'expiring' | 'expired'

/** Same threshold the server alerts at. */
export const SSL_WARNING_DAYS = 14

export function sslState(daysLeft: number): SslState {
  if (daysLeft < 0) return 'expired'
  return daysLeft < SSL_WARNING_DAYS ? 'expiring' : 'ok'
}

/** Share of checks that were up, 0 to 100; null without checks. */
export function uptimePercent(checks: readonly Check[]): number | null {
  if (checks.length === 0) return null
  return (checks.filter((c) => c.up).length / checks.length) * 100
}

/** Response times of the checks that got an answer. */
export function responseTimes(checks: readonly Check[]): number[] {
  return checks.flatMap((c) => (c.up && c.responseTimeMs !== null ? [c.responseTimeMs] : []))
}

export function average(values: readonly number[]): number | null {
  return values.length === 0 ? null : values.reduce((sum, v) => sum + v, 0) / values.length
}

/** Nearest-rank percentile; {@code p} between 0 and 100. */
export function percentile(values: readonly number[], p: number): number | null {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.max(0, Math.ceil((p / 100) * sorted.length) - 1)]
}

/** Uptime for display: 100 only when nothing failed, so 99.99 never rounds up to "100%". */
export function formatUptime(percent: number): string {
  if (percent === 100) return '100%'
  const rounded = Math.min(99.9, Math.floor(percent * 10) / 10)
  return `${rounded.toFixed(1)}%`
}
