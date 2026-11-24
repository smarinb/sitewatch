import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { bodiesSentTo, check, mockApi, signIn, site } from '../test/mocks.ts'

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000 + 3_600_000).toISOString()

describe('dashboard', () => {
  it('lists sites with status, latest metrics and certificate warnings', async () => {
    signIn()
    mockApi({
      'GET /api/sites': {
        json: [
          site({ id: 1, name: 'Shop', url: 'https://shop.example.com/', status: 'UP' }),
          site({ id: 2, name: 'Blog', url: 'https://blog.example.com/', status: 'DOWN' }),
          site({ id: 3, name: 'Old site', url: 'https://old.example.com/', active: false, status: 'UP' }),
          site({ id: 4, name: 'Fresh', url: 'https://fresh.example.com/', status: 'UNKNOWN', lastCheckedAt: null }),
        ],
      },
      'GET /api/checks/latest': {
        json: [
          { siteId: 1, ...check(2, { responseTimeMs: 312, sslExpiresAt: inDays(10), wpVersion: '6.7.1' }) },
          { siteId: 2, ...check(2, { up: false, httpStatus: 503, responseTimeMs: null, sslExpiresAt: inDays(60) }) },
        ],
      },
    })

    renderApp('/')

    const row = (name: string) => within(screen.getByRole('link', { name }).closest('tr')!)
    expect(await screen.findByRole('link', { name: 'Shop' })).toHaveAttribute('href', '/sites/1')
    expect(row('Shop').getByText('Up')).toBeInTheDocument()
    expect(row('Shop').getByText('312 ms')).toBeInTheDocument()
    expect(row('Shop').getByText('10 days left')).toBeInTheDocument()
    expect(row('Shop').getByText('6.7.1')).toBeInTheDocument()
    expect(row('Shop').getByText('shop.example.com')).toBeInTheDocument()
    expect(row('Blog').getByText('Down')).toBeInTheDocument()
    expect(row('Blog').getByText('—', { selector: 'td.num' })).toBeInTheDocument()
    expect(row('Old site').getByText('Paused')).toBeInTheDocument()
    expect(row('Fresh').getByText('Pending')).toBeInTheDocument()
    expect(row('Fresh').getByText('Not yet')).toBeInTheDocument()

    // Counts ignore paused sites.
    const tile = (label: string) => screen.getByText(label, { selector: '.tile-label' }).parentElement!
    expect(tile('Sites')).toHaveTextContent('4')
    expect(tile('Up')).toHaveTextContent('1')
    expect(tile('Down')).toHaveTextContent('1')
  })

  it('invites the user to add a first site', async () => {
    signIn()
    mockApi({ 'GET /api/sites': { json: [] }, 'GET /api/checks/latest': { json: [] } })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'No sites yet' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('adds a site, completing a bare host to https', async () => {
    const user = userEvent.setup()
    signIn()
    const sites = [site({ id: 1, name: 'Shop' })]
    const mock = mockApi({
      'GET /api/sites': () => ({ json: sites }),
      'GET /api/checks/latest': { json: [] },
      'POST /api/sites': ({ body }) => {
        sites.push(site({ id: 2, name: 'New blog', url: (body as { url: string }).url }))
        return { status: 201, json: sites[1] }
      },
    })

    renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Add site' }))
    await user.type(screen.getByLabelText('Name'), 'New blog')
    await user.type(screen.getByLabelText('URL'), 'blog.example.com')
    await user.selectOptions(screen.getByLabelText('Check'), 'Every 15 minutes')
    await user.click(screen.getByRole('button', { name: 'Add site', description: '' }))

    expect(await screen.findByRole('link', { name: 'New blog' })).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'POST /api/sites')).toEqual([
      { name: 'New blog', url: 'https://blog.example.com', checkIntervalMinutes: 15, active: true },
    ])
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument()
  })

  it('shows the server validation error under the field and keeps the form open', async () => {
    const user = userEvent.setup()
    signIn()
    mockApi({
      'GET /api/sites': { json: [] },
      'GET /api/checks/latest': { json: [] },
      'POST /api/sites': { status: 409, json: { detail: 'You are already monitoring this URL' } },
    })

    renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Add your first site' }))
    await user.type(screen.getByLabelText('Name'), 'Dup')
    await user.type(screen.getByLabelText('URL'), 'https://dup.example.com')
    await user.click(screen.getByRole('button', { name: 'Add site' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('You are already monitoring this URL')
    expect(screen.getByLabelText('Name')).toHaveValue('Dup')
  })

  it('offers a retry when the sites cannot be loaded', async () => {
    const user = userEvent.setup()
    signIn()
    let failing = true
    mockApi({
      'GET /api/sites': () => (failing ? { status: 500, json: { detail: 'Database unavailable' } } : { json: [] }),
      'GET /api/checks/latest': { json: [] },
    })

    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable')

    failing = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'No sites yet' })).toBeInTheDocument()
  })
})
