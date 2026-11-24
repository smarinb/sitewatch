import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/render.tsx'
import { bodiesSentTo, mockApi, signIn } from '../test/mocks.ts'

describe('alert settings', () => {
  it('saves the Telegram chat id, then sends a test alert and reports each channel', async () => {
    const user = userEvent.setup()
    signIn()
    const mock = mockApi({
      'GET /api/me/notifications': { json: { emailAlerts: true, telegramChatId: null } },
      'PUT /api/me/notifications': ({ body }) => ({ json: body }),
      'POST /api/me/notifications/test': { json: { email: 'sent', telegram: 'not configured on the server' } },
    })

    renderApp('/settings')
    const save = await screen.findByRole('button', { name: 'Save' })
    const testButton = screen.getByRole('button', { name: 'Send test alert' })
    expect(save).toBeDisabled()

    await user.type(screen.getByLabelText('Telegram chat id'), ' 616828960 ')
    expect(save).toBeEnabled()
    expect(testButton).toBeDisabled()
    expect(screen.getByText('Save your changes first.')).toBeInTheDocument()

    await user.click(save)
    expect(await screen.findByText('Saved')).toBeInTheDocument()
    expect(bodiesSentTo(mock, 'PUT /api/me/notifications')).toEqual([{ emailAlerts: true, telegramChatId: '616828960' }])
    expect(screen.getByLabelText('Telegram chat id')).toHaveValue('616828960')

    await user.click(testButton)
    const results = await screen.findByRole('list', { name: 'Test alert results' })
    expect(results).toHaveTextContent('email sent')
    expect(results).toHaveTextContent('telegram not configured on the server')
  })

  it('turns Telegram off by clearing the id, and email off with the checkbox', async () => {
    const user = userEvent.setup()
    signIn()
    const mock = mockApi({
      'GET /api/me/notifications': { json: { emailAlerts: true, telegramChatId: '616828960' } },
      'PUT /api/me/notifications': ({ body }) => ({ json: body }),
    })

    renderApp('/settings')
    await user.clear(await screen.findByLabelText('Telegram chat id'))
    await user.click(screen.getByLabelText('Email alerts to your account address'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await screen.findByText('Saved')
    expect(bodiesSentTo(mock, 'PUT /api/me/notifications')).toEqual([{ emailAlerts: false, telegramChatId: null }])
  })

  it('shows the validation error for a bad chat id', async () => {
    const user = userEvent.setup()
    signIn()
    mockApi({
      'GET /api/me/notifications': { json: { emailAlerts: true, telegramChatId: null } },
      'PUT /api/me/notifications': { status: 400, json: { detail: 'Validation failed', errors: { telegramChatId: 'must be a numeric Telegram chat id' } } },
    })

    renderApp('/settings')
    await user.type(await screen.findByLabelText('Telegram chat id'), '@mybot')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('must be a numeric Telegram chat id')).toBeInTheDocument()
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
  })
})
