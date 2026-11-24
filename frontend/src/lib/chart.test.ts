import { describe, expect, it } from 'vitest'
import { check } from '../test/mocks.ts'
import { lineSegments, nearestIndex, niceScale, outages, toPoints } from './chart.ts'

describe('niceScale', () => {
  it.each([
    [312, [0, 100, 200, 300, 400]],
    [95, [0, 25, 50, 75, 100]],
    [1800, [0, 500, 1000, 1500, 2000]],
    [7, [0, 2, 4, 6, 8]],
  ])('rounds %d up to clean ticks', (max, ticks) => {
    const scale = niceScale(max)
    expect(scale.ticks).toEqual(ticks)
    expect(scale.max).toBe(ticks[ticks.length - 1])
    expect(scale.max).toBeGreaterThanOrEqual(max)
  })

  it('still draws an axis when there is no data', () => {
    expect(niceScale(0).ticks.length).toBeGreaterThan(1)
  })
})

describe('toPoints and lineSegments', () => {
  it('orders oldest first and gives failed checks no value', () => {
    const points = toPoints([check(0, { responseTimeMs: 120 }), check(5, { up: false, responseTimeMs: null }), check(10)])

    expect(points.map((p) => p.value)).toEqual([300, null, 120])
    expect(points[0].time).toBeLessThan(points[2].time)
  })

  it('breaks the line at a failed check instead of bridging it', () => {
    const points = toPoints([check(0), check(5, { up: false, responseTimeMs: null }), check(10), check(15)])

    const segments = lineSegments(points)

    expect(segments.map((s) => s.length)).toEqual([2, 1])
  })

  it('has no segments when every check failed', () => {
    expect(lineSegments(toPoints([check(0, { up: false, responseTimeMs: null })]))).toEqual([])
  })
})

describe('outages', () => {
  it('groups consecutive failures and keeps separate outages apart', () => {
    const down = { up: false, responseTimeMs: null }
    const points = toPoints([check(0, down), check(5), check(10, down), check(15, down), check(20, down), check(25), check(30, down)])

    expect(outages(points).map((g) => g.length)).toEqual([1, 3, 1])
  })

  it('finds none when everything answered', () => {
    expect(outages(toPoints([check(0), check(5)]))).toEqual([])
  })
})

describe('nearestIndex', () => {
  const xs = [10, 20, 40, 80]

  it.each([
    [0, 0],
    [14, 0],
    [16, 1],
    [31, 2],
    [59, 2],
    [61, 3],
    [500, 3],
  ])('x=%d picks index %d', (x, expected) => {
    expect(nearestIndex(xs, x)).toBe(expected)
  })

  it('returns -1 without points', () => {
    expect(nearestIndex([], 5)).toBe(-1)
  })
})
