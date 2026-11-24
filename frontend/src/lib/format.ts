const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

export function formatMs(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(ms < 10_000 ? 2 : 1)} s`
}

/** "just now", "5 min ago", "3 h ago", "2 d ago". */
export function relativeTime(iso: string, now: number = Date.now()): string {
  const elapsed = Math.max(0, now - new Date(iso).getTime())
  if (elapsed < MINUTE) return 'just now'
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} min ago`
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)} h ago`
  return `${Math.floor(elapsed / DAY)} d ago`
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(iso))
}

/** Whole days until the date; negative once it has passed. */
export function daysUntil(iso: string, now: number = Date.now()): number {
  return Math.floor((new Date(iso).getTime() - now) / DAY)
}

export function hostname(url: string): string {
  try {
    return new URL(url).host
  } catch {
    return url
  }
}
