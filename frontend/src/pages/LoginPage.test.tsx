import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { bodiesSentTo, fakeJwt, mockApi, signIn } from '../test/mocks.ts'

describe('authentication', () => {
  it('sends signed-out visitors to the login and back to the page they wanted', async () => {
    const user = userEvent.setup()
    const mock = mockApi({
      'POST /api/auth/login': { json: { accessToken: fakeJwt('ana@example.com'), tokenType: 'Bearer', expiresAt: '' } },
      'GET /api/me/notifications': { json: { emailAlerts: true, telegramChatId: null } },
    })

    renderApp('/settings')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'ana@example.com')
    await user.type(screen.getByLabelText('Password'), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Alerts' })).toBeInTheDocument()
    expect(screen.getByText('ana@example.com')).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'POST /api/auth/login')).toEqual([{ email: 'ana@example.com', password: 'secret-password' }])
    expect(localStorage.getItem('sitewatch.token')).not.toBeNull()
    // The settings request after sign-in already carries the new token.
    const settingsCall = mock.mock.calls.find(([url]) => String(url).endsWith('/api/me/notifications'))!
    expect(new Headers(settingsCall[1]?.headers).get('Authorization')).toMatch(/^Bearer /)
  })

  it('shows why a sign-in failed and lets the user retry', async () => {
    const user = userEvent.setup()
    mockApi({ 'POST /api/auth/login': { status: 401, json: { detail: 'Invalid email or password' } } })

    renderApp('/login')
    await user.type(screen.getByLabelText('Email'), 'ana@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('registers a new account and shows server-side field errors', async () => {
    const user = userEvent.setup()
    mockApi({
      'GET /api/auth/config': { json: { registrationOpen: true, demoAvailable: false } },
      'POST /api/auth/register': { status: 400, json: { detail: 'Validation failed', errors: { email: 'must be a well-formed email address' } } },
    })

    renderApp('/login')
    await user.click(await screen.findByRole('button', { name: 'Create an account' }))
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
    expect(screen.getByText('At least 8 characters.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'nope')
    await user.type(screen.getByLabelText('Password'), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInvalid()
  })

  it('skips the login for a signed-in user', async () => {
    signIn()
    mockApi({ 'GET /api/sites': { json: [] }, 'GET /api/checks/latest': { json: [] } })

    renderApp('/login')

    expect(await screen.findByRole('heading', { name: 'Sites' })).toBeInTheDocument()
  })

  it('returns to the login when the server rejects the token', async () => {
    signIn()
    mockApi({
      'GET /api/sites': { status: 401, json: { detail: 'Unauthorized' } },
      'GET /api/checks/latest': { status: 401, json: { detail: 'Unauthorized' } },
    })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await waitFor(() => expect(localStorage.getItem('sitewatch.token')).toBeNull())
  })

  it('signs out', async () => {
    const user = userEvent.setup()
    signIn()
    mockApi({ 'GET /api/sites': { json: [] }, 'GET /api/checks/latest': { json: [] } })

    renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(localStorage.getItem('sitewatch.token')).toBeNull()
  })

  it('offers the read-only demo and no sign-up when the deployment is a public demo', async () => {
    const user = userEvent.setup()
    const mock = mockApi({
      'GET /api/auth/config': { json: { registrationOpen: false, demoAvailable: true } },
      'POST /api/auth/demo': { json: { accessToken: fakeJwt('demo@sergiomarin.dev', 3600, { readOnly: true }), tokenType: 'Bearer', expiresAt: '' } },
      'GET /api/sites': { json: [] },
      'GET /api/checks/latest': { json: [] },
    })

    renderApp('/login')
    expect(await screen.findByText('Sign-ups are closed on this public demo.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create an account' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Try the demo' }))

    expect(await screen.findByRole('heading', { name: 'Sites' })).toBeInTheDocument()
    expect(screen.getByText(/Read-only demo/)).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'POST /api/auth/demo')).toHaveLength(1)
  })

  it('keeps sign-up and shows no demo button when the server offers neither', async () => {
    mockApi({ 'GET /api/auth/config': { json: { registrationOpen: true, demoAvailable: false } } })

    renderApp('/login')

    expect(await screen.findByRole('button', { name: 'Create an account' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try the demo' })).not.toBeInTheDocument()
  })
})
