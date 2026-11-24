import { useMemo, useState, type KeyboardEvent, type PointerEvent } from 'react'
import type { Check } from '../api/types.ts'
import { lineSegments, nearestIndex, niceScale, outages, toPoints, type Point } from '../lib/chart.ts'
import { formatDateTime, formatMs } from '../lib/format.ts'
import { useElementWidth } from './useElementWidth.ts'

const HEIGHT = 260
const MARGIN = { top: 16, right: 20, bottom: 30, left: 52 }
const HOUR = 3_600_000
/** Beside the tooltip's half width, so it never leaves the chart. */
const TOOLTIP_EDGE = 96
/** Below this width the time axis gets fewer labels, so they never overlap. */
const NARROW = 520

interface Props {
  /** Newest first, as returned by the API. */
  checks: Check[]
  /** Length of the time window shown, ending at the newest check. */
  rangeMs: number
  /** Dim the chart while a reload is in flight; the previous render stays put. */
  refreshing?: boolean
}

export function ResponseChart({ checks, rangeMs, refreshing = false }: Props) {
  const [wrapperRef, width] = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<number | null>(null)
  const [announce, setAnnounce] = useState(false)

  const points = useMemo(() => toPoints(checks), [checks])
  const layout = useMemo(() => {
    const plotWidth = Math.max(width - MARGIN.left - MARGIN.right, 1)
    const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom
    const end = points.length > 0 ? points[points.length - 1].time : 0
    const start = end - rangeMs
    const scale = niceScale(Math.max(0, ...points.map((p) => p.value ?? 0)))
    const baseline = MARGIN.top + plotHeight
    const x = (time: number) => MARGIN.left + ((time - start) / rangeMs) * plotWidth
    const y = (value: number) => baseline - (value / scale.max) * plotHeight
    return { plotWidth, plotHeight, start, end, scale, baseline, x, y }
  }, [points, rangeMs, width])

  if (points.length === 0) {
    return <p className="empty">No checks in this time range yet.</p>
  }

  const { scale, baseline, x, y, start, end } = layout
  const xs = points.map((p) => x(p.time))
  const segments = lineSegments(points)
  const failed = points.filter((p) => p.value === null)
  const outageGroups = outages(points)
  const last = points[points.length - 1]
  const current = active === null ? null : points[active]

  const pathOf = (segment: Point[]) => segment.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.time)} ${y(p.value!)}`).join(' ')
  const areaOf = (segment: Point[]) =>
    `${pathOf(segment)} L${x(segment[segment.length - 1].time)} ${baseline} L${x(segment[0].time)} ${baseline} Z`

  const onPointerMove = (event: PointerEvent<SVGRectElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    setAnnounce(false)
    setActive(nearestIndex(xs, event.clientX - rect.left + MARGIN.left))
  }

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const from = active ?? points.length - 1
    const moves: Record<string, number> = {
      ArrowLeft: Math.max(0, from - 1),
      ArrowRight: Math.min(points.length - 1, from + 1),
      Home: 0,
      End: points.length - 1,
    }
    if (event.key === 'Escape') {
      setActive(null)
    } else if (event.key in moves) {
      event.preventDefault()
      setAnnounce(true)
      setActive(active === null ? points.length - 1 : moves[event.key])
    }
  }

  const tickCount = width < NARROW ? 3 : 5
  const tickTimes = Array.from({ length: tickCount }, (_, i) => start + ((end - start) * i) / (tickCount - 1))
  const tickLabel = (time: number) =>
    new Intl.DateTimeFormat(undefined, rangeMs <= 36 * HOUR ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' }).format(time)

  return (
    <div className={refreshing ? 'chart is-refreshing' : 'chart'}>
      <div
        ref={wrapperRef}
        className="chart-frame"
        tabIndex={0}
        role="group"
        aria-label="Response time chart. Use the left and right arrow keys to move between checks."
        onKeyDown={onKeyDown}
        onBlur={() => {
          setActive(null)
          setAnnounce(false)
        }}
      >
        <svg width={width} height={HEIGHT} aria-hidden="true">
          {scale.ticks.map((tick) => (
            <g key={tick}>
              <line className={tick === 0 ? 'chart-axis' : 'chart-grid'} x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)} />
              <text className="chart-tick" x={MARGIN.left - 8} y={y(tick)} textAnchor="end" dominantBaseline="middle">
                {tick}
              </text>
            </g>
          ))}
          {tickTimes.map((time, i) => (
            <text key={i} className="chart-tick" x={x(time)} y={baseline + 20} textAnchor={i === 0 ? 'start' : i === tickCount - 1 ? 'end' : 'middle'}>
              {tickLabel(time)}
            </text>
          ))}

          {segments.map((segment, i) => (
            <g key={i}>
              {segment.length > 1 && <path className="chart-area" d={areaOf(segment)} />}
              {segment.length > 1 ? (
                <path className="chart-line" d={pathOf(segment)} />
              ) : (
                <circle className="chart-dot" cx={x(segment[0].time)} cy={y(segment[0].value!)} r={4} />
              )}
            </g>
          ))}

          {outageGroups.map((group) =>
            group.length === 1 ? (
              <circle key={group[0].time} className="chart-failed" cx={x(group[0].time)} cy={baseline} r={5} />
            ) : (
              <g key={group[0].time}>
                <rect className="chart-outage-wash" x={x(group[0].time)} y={MARGIN.top} width={x(group[group.length - 1].time) - x(group[0].time)} height={baseline - MARGIN.top} />
                <rect className="chart-outage-bar" x={x(group[0].time) - 2} y={baseline - 3} width={x(group[group.length - 1].time) - x(group[0].time) + 4} height={6} rx={3} />
              </g>
            ),
          )}

          {last.value !== null && (
            <>
              <circle className="chart-dot" cx={x(last.time)} cy={y(last.value)} r={4} />
              {width >= NARROW && (
              <text className="chart-end-label" x={x(last.time) - 8} y={Math.max(y(last.value) - 14, MARGIN.top + 8)} textAnchor="end">
                {formatMs(last.value)}
              </text>
              )}
            </>
          )}

          {current && (
            <>
              <line className="chart-axis" x1={x(current.time)} x2={x(current.time)} y1={MARGIN.top} y2={baseline} />
              <circle className={current.value === null ? 'chart-failed' : 'chart-dot'} cx={x(current.time)} cy={current.value === null ? baseline : y(current.value)} r={current.value === null ? 6 : 5} />
            </>
          )}

          <rect
            className="chart-hit"
            x={MARGIN.left}
            y={MARGIN.top}
            width={layout.plotWidth}
            height={layout.plotHeight + 12}
            fill="transparent"
            onPointerMove={onPointerMove}
            onPointerLeave={() => setActive(null)}
          />
        </svg>

        {current && (
          <div className="tooltip" style={{ left: Math.min(Math.max(x(current.time), TOOLTIP_EDGE), width - TOOLTIP_EDGE) }} aria-hidden="true">
            <TooltipBody point={current} />
          </div>
        )}
        <div className="sr-only" aria-live="polite">
          {announce && current ? describe(current) : ''}
        </div>
      </div>

      {failed.length > 0 && (
        <p className="chart-key">
          <span className="key-dot" aria-hidden="true" /> {failed.length === 1 ? 'Failed check' : 'Failed checks'} ({failed.length})
        </p>
      )}
    </div>
  )
}

function describe(point: Point): string {
  const when = formatDateTime(new Date(point.time).toISOString())
  return point.value === null
    ? `${when}: failed check. ${point.check.error ?? ''}`
    : `${when}: ${formatMs(point.value)}, HTTP ${point.check.httpStatus}`
}

function TooltipBody({ point }: { point: Point }) {
  const { check } = point
  return (
    <>
      <div className="tooltip-value">
        {point.value === null ? 'Failed check' : formatMs(point.value)}
      </div>
      <div className="tooltip-meta">
        {point.value !== null && <span className="key-line" aria-hidden="true" />}
        {point.value !== null ? 'Response time' : (check.error ?? 'No response')}
      </div>
      <div className="tooltip-meta">{formatDateTime(check.checkedAt)}</div>
      {check.httpStatus !== null && <div className="tooltip-meta">HTTP {check.httpStatus}</div>}
    </>
  )
}
