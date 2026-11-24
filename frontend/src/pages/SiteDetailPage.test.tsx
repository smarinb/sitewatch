import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { bodiesSentTo, check, mockApi, signIn, site } from '../test/mocks.ts'

const history = [
  check(1, { responseTimeMs: 400, sslExpiresAt: new Date(Date.now() + 40 * 86_400_000).toISOString(), wpVersion: '6.7.1' }),
  check(6, { responseTimeMs: 200 }),
  check(11, { up: false, httpStatus: 503, responseTimeMs: null, error: 'HTTP 503' }),
]

function mockSite(extra: Parameters<typeof mockApi>[0] = {}) {
  return mockApi({
    'GET /api/sites/1': { json: site() },
    'GET /api/sites/1/checks': { json: history },
    ...extra,
  })
}

const tile = (label: string) => within(screen.getByText(label, { selector: '.tile-label' }).parentElement!)

describe('site detail', () => {
  it('summarises the selected range', async () => {
    signIn()
    mockSite()

    renderApp('/sites/1')

    expect(await screen.findByRole('heading', { name: /Shop/ })).toBeInTheDocument()
    expect(await screen.findByText('66.6%')).toBeInTheDocument()
    expect(tile('Uptime').getByText('3 checks')).toBeInTheDocument()
    expect(tile('Avg response').getByText('300 ms')).toBeInTheDocument()
    expect(tile('Avg response').getByText('95% under 400 ms')).toBeInTheDocument()
    expect(tile('SSL certificate').getByText('39 days')).toBeInTheDocument()
    expect(tile('WordPress').getByText('6.7.1')).toBeInTheDocument()
  })

  it('asks the API for the chosen time range', async () => {
    const user = userEvent.setup()
    signIn()
    const mock = mockSite()

    renderApp('/sites/1')
    await screen.findByText('66.6%')
    await user.click(screen.getByRole('button', { name: 'Last hour' }))

    expect(screen.getByRole('button', { name: 'Last hour' })).toHaveAttribute('aria-pressed', 'true')
    const requests = mock.mock.calls.map(([url]) => new URL(String(url))).filter((u) => u.pathname.endsWith('/checks'))
    expect(requests.at(-1)!.searchParams.get('limit')).toBe('500')
    const since = Date.parse(requests.at(-1)!.searchParams.get('since')!)
    expect(Date.now() - since).toBeGreaterThan(3_500_000)
    expect(Date.now() - since).toBeLessThan(3_700_000)
    expect(requests.at(-2)!.searchParams.get('since')).not.toBe(requests.at(-1)!.searchParams.get('since'))
  })

  it('shows the same checks as a table', async () => {
    const user = userEvent.setup()
    signIn()
    mockSite()

    renderApp('/sites/1')
    await screen.findByText('66.6%')
    expect(screen.getByRole('group', { name: /Response time chart/ })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Table' }))

    const table = screen.getByRole('table')
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getByText('400 ms')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Down')).toBeInTheDocument()
    expect(within(rows[2]).getByText('HTTP 503')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: /Response time chart/ })).not.toBeInTheDocument()
  })

  it('shows the outage reason while the site is down', async () => {
    signIn()
    mockApi({
      'GET /api/sites/1': { json: site({ status: 'DOWN' }) },
      'GET /api/sites/1/checks': { json: [check(1, { up: false, httpStatus: null, responseTimeMs: null, error: 'Connection refused' })] },
    })

    renderApp('/sites/1')

    expect(await screen.findByText('Connection refused')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Down.')
  })

  it('edits a site, keeping every field because PUT replaces it', async () => {
    const user = userEvent.setup()
    signIn()
    const mock = mockSite({ 'PUT /api/sites/1': { json: site({ name: 'Shop 2' }) } })

    renderApp('/sites/1')
    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Shop 2')
    await user.click(screen.getByLabelText(/Monitoring active/))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await screen.findByRole('button', { name: 'Edit' })
    expect(bodiesSentTo(mock, 'PUT /api/sites/1')).toEqual([
      { name: 'Shop 2', url: 'https://shop.example.com/', checkIntervalMinutes: 5, active: false },
    ])
  })

  it('deletes only after confirmation', async () => {
    const user = userEvent.setup()
    signIn()
    const mock = mockSite({
      'DELETE /api/sites/1': { status: 204 },
      'GET /api/sites': { json: [] },
      'GET /api/checks/latest': { json: [] },
    })

    renderApp('/sites/1')
    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Delete Shop?')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(bodiesSentTo(mock, 'DELETE /api/sites/1')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByRole('heading', { name: 'No sites yet' })).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'DELETE /api/sites/1')).toHaveLength(1)
  })

  it('says so when the site does not exist or belongs to someone else', async () => {
    signIn()
    mockApi({
      'GET /api/sites/99': { status: 404, json: { detail: 'Site 99 not found' } },
      'GET /api/sites/99/checks': { status: 404, json: { detail: 'Site 99 not found' } },
    })

    renderApp('/sites/99')

    expect(await screen.findByRole('heading', { name: 'Site not found' })).toBeInTheDocument()
  })
})
