import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { check, mockApi, signIn, site } from '../test/mocks.ts'

const demo = () => signIn('demo@sergiomarin.dev', { readOnly: true })

describe('read-only demo account', () => {
  it('shows the banner and hides "Add site" on the dashboard', async () => {
    demo()
    mockApi({ 'GET /api/sites': { json: [site()] }, 'GET /api/checks/latest': { json: [] } })

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sites' })).toBeInTheDocument()
    expect(screen.getByText(/Read-only demo/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add site' })).not.toBeInTheDocument()
  })

  it('hides Edit and Delete on a site', async () => {
    demo()
    mockApi({ 'GET /api/sites/1': { json: site() }, 'GET /api/sites/1/checks': { json: [check(1)] } })

    renderApp('/sites/1')

    expect(await screen.findByRole('heading', { name: /Shop/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('shows the alert settings but cannot change them or send test alerts', async () => {
    demo()
    mockApi({ 'GET /api/me/notifications': { json: { emailAlerts: true, telegramChatId: null, emailVerified: true } } })

    renderApp('/settings')

    expect(await screen.findByLabelText('Telegram chat id')).toBeDisabled()
    expect(screen.getByLabelText('Email alerts to your account address')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send test alert' })).toBeDisabled()
  })

  it('does not show the banner to a regular account', async () => {
    signIn()
    mockApi({ 'GET /api/sites': { json: [site()] }, 'GET /api/checks/latest': { json: [] } })

    renderApp('/')

    expect(await screen.findByRole('button', { name: 'Add site' })).toBeInTheDocument()
    expect(screen.queryByText(/Read-only demo/)).not.toBeInTheDocument()
  })
})
