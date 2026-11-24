import type { Check } from '../api/types.ts'

export interface Scale {
  /** Top of the axis, a multiple of the step. */
  max: number
  ticks: number[]
}

/** A zero-based axis with round tick values (1, 2, 2.5, 5 times a power of ten). */
export function niceScale(maxValue: number, tickCount = 4): Scale {
  const target = Math.max(maxValue, 1)
  const rough = target / tickCount
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? 10 * magnitude
  const count = Math.ceil(target / step)
  const ticks = Array.from({ length: count + 1 }, (_, i) => Math.round(i * step * 1000) / 1000)
  return { max: ticks[ticks.length - 1], ticks }
}

export interface Point {
  /** Epoch milliseconds. */
  time: number
  /** Response time for a check that got an answer; null for a failed one. */
  value: number | null
  check: Check
}

/** Checks as chart points, oldest first (the API returns newest first). */
export function toPoints(checks: readonly Check[]): Point[] {
  return checks
    .map((check) => ({
      time: new Date(check.checkedAt).getTime(),
      value: check.up && check.responseTimeMs !== null ? check.responseTimeMs : null,
      check,
    }))
    .sort((a, b) => a.time - b.time)
}

/** Runs of consecutive points with a value; a failed check breaks the line instead of being bridged. */
export function lineSegments(points: readonly Point[]): Point[][] {
  const segments: Point[][] = []
  let current: Point[] = []
  for (const point of points) {
    if (point.value === null) {
      if (current.length > 0) segments.push(current)
      current = []
    } else {
      current.push(point)
    }
  }
  if (current.length > 0) segments.push(current)
  return segments
}

/** Runs of consecutive failed checks: a long outage is one band, not a pile of overlapping markers. */
export function outages(points: readonly Point[]): Point[][] {
  const groups: Point[][] = []
  let current: Point[] = []
  for (const point of points) {
    if (point.value === null) {
      current.push(point)
    } else if (current.length > 0) {
      groups.push(current)
      current = []
    }
  }
  if (current.length > 0) groups.push(current)
  return groups
}

/** Index of the point whose x is closest to {@code x}; -1 for no points. {@code xs} must be ascending. */
export function nearestIndex(xs: readonly number[], x: number): number {
  if (xs.length === 0) return -1
  let low = 0
  let high = xs.length - 1
  while (low < high) {
    const mid = (low + high) >> 1
    if (xs[mid] < x) low = mid + 1
    else high = mid
  }
  return low > 0 && x - xs[low - 1] <= xs[low] - x ? low - 1 : low
}
