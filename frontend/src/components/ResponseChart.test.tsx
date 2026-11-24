import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { check } from '../test/mocks.ts'
import { ResponseChart } from './ResponseChart.tsx'

const HOUR = 3_600_000
// Newest first, like the API.
const checks = [
  check(0, { responseTimeMs: 400 }),
  check(5, { up: false, httpStatus: 503, responseTimeMs: null, error: 'HTTP 503' }),
  check(10, { responseTimeMs: 200 }),
  check(15, { responseTimeMs: 250 }),
]

const tooltip = (container: HTMLElement) => container.querySelector('.tooltip')

describe('ResponseChart', () => {
  it('draws one line segment per run of answered checks and marks failures separately', () => {
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} />)

    expect(container.querySelectorAll('.chart-line')).toHaveLength(1)
    expect(container.querySelectorAll('.chart-area')).toHaveLength(1)
    expect(container.querySelectorAll('.chart-failed')).toHaveLength(1)
    expect(screen.getByText('Failed check (1)')).toBeInTheDocument()
    expect(container.querySelector('.chart-end-label')).toHaveTextContent('400 ms')
  })

  it('draws a long outage as one band instead of a marker per failed check', () => {
    const outage = [
      check(0, { responseTimeMs: 300 }),
      check(5, { up: false, responseTimeMs: null }),
      check(10, { up: false, responseTimeMs: null }),
      check(15, { up: false, responseTimeMs: null }),
      check(20, { responseTimeMs: 300 }),
    ]

    const { container } = render(<ResponseChart checks={outage} rangeMs={HOUR} />)

    expect(container.querySelectorAll('.chart-outage-bar')).toHaveLength(1)
    expect(container.querySelectorAll('.chart-outage-wash')).toHaveLength(1)
    expect(container.querySelectorAll('.chart-failed')).toHaveLength(0)
    expect(screen.getByText('Failed checks (3)')).toBeInTheDocument()
  })

  it('labels the axis with round values', () => {
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} />)

    const ticks = [...container.querySelectorAll('.chart-tick')].map((t) => t.textContent)
    expect(ticks).toEqual(expect.arrayContaining(['0', '100', '200', '300', '400']))
  })

  it('has no failed-check key when everything was up', () => {
    render(<ResponseChart checks={[check(0), check(5)]} rangeMs={HOUR} />)

    expect(screen.queryByText(/Failed check/)).not.toBeInTheDocument()
  })

  it('walks through the checks with the arrow keys, showing a tooltip for each', async () => {
    const user = userEvent.setup()
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} />)
    const chart = screen.getByRole('group', { name: /Response time chart/ })
    expect(tooltip(container)).toBeNull()

    act(() => chart.focus())
    await user.keyboard('{ArrowLeft}')
    expect(tooltip(container)).toHaveTextContent('400 ms')

    await user.keyboard('{ArrowLeft}')
    expect(tooltip(container)).toHaveTextContent('Failed check')
    expect(tooltip(container)).toHaveTextContent('HTTP 503')
    expect(screen.getByText(/failed check\. HTTP 503/i, { selector: '[aria-live]' })).toBeInTheDocument()

    await user.keyboard('{ArrowLeft}')
    expect(tooltip(container)).toHaveTextContent('200 ms')

    await user.keyboard('{Home}')
    expect(tooltip(container)).toHaveTextContent('250 ms')

    await user.keyboard('{Escape}')
    expect(tooltip(container)).toBeNull()
  })

  it('stops at both ends instead of wrapping', async () => {
    const user = userEvent.setup()
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} />)
    act(() => screen.getByRole('group', { name: /Response time chart/ }).focus())

    await user.keyboard('{End}{ArrowRight}{ArrowRight}')
    expect(tooltip(container)).toHaveTextContent('400 ms')

    await user.keyboard('{Home}{ArrowLeft}')
    expect(tooltip(container)).toHaveTextContent('250 ms')
  })

  it('hides the tooltip when focus leaves', async () => {
    const user = userEvent.setup()
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} />)
    act(() => screen.getByRole('group', { name: /Response time chart/ }).focus())
    await user.keyboard('{ArrowLeft}')
    expect(tooltip(container)).not.toBeNull()

    await user.tab()

    expect(tooltip(container)).toBeNull()
  })

  it('dims the previous render while reloading', () => {
    const { container } = render(<ResponseChart checks={checks} rangeMs={HOUR} refreshing />)

    expect(container.firstElementChild).toHaveClass('is-refreshing')
  })

  it('says so when there is nothing to draw', () => {
    render(<ResponseChart checks={[]} rangeMs={HOUR} />)

    expect(screen.getByText('No checks in this time range yet.')).toBeInTheDocument()
  })

  it('draws a lone answered check as a dot, since a line needs two points', () => {
    const { container } = render(<ResponseChart checks={[check(0)]} rangeMs={HOUR} />)

    expect(container.querySelectorAll('.chart-line')).toHaveLength(0)
    expect(container.querySelectorAll('.chart-dot').length).toBeGreaterThan(0)
  })
})
