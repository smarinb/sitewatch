import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { bodiesSentTo, mockApi, signIn } from '../test/mocks.ts'

describe('email confirmation page', () => {
  it('confirms the address with the token from the link, once, without needing a session', async () => {
    const mock = mockApi({ 'POST /api/auth/verify-email': { status: 204 } })

    renderApp('/verify-email?token=abc_123-XYZ')

    expect(await screen.findByRole('heading', { name: 'Email confirmed' })).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'POST /api/auth/verify-email')).toEqual([{ token: 'abc_123-XYZ' }])
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
  })

  it('offers the dashboard to a user who is already signed in', async () => {
    signIn()
    mockApi({ 'POST /api/auth/verify-email': { status: 204 } })

    renderApp('/verify-email?token=abc')

    expect(await screen.findByRole('link', { name: 'Go to your sites' })).toHaveAttribute('href', '/')
  })

  it('explains an expired or used link and points to a new one', async () => {
    mockApi({
      'POST /api/auth/verify-email': { status: 400, json: { detail: 'This verification link is invalid or has expired' } },
    })

    renderApp('/verify-email?token=old')

    expect(await screen.findByRole('heading', { name: 'We could not confirm your email' })).toBeInTheDocument()
    expect(screen.getByText('This verification link is invalid or has expired')).toBeInTheDocument()
    expect(screen.getByText('Sign in and open Alerts to get a new link.')).toBeInTheDocument()
  })

  it('does not call the server when the link has no token', async () => {
    const mock = mockApi({})

    renderApp('/verify-email')

    expect(await screen.findByText(/link is incomplete/)).toBeInTheDocument()
    expect(mock).not.toHaveBeenCalled()
  })
})
