import { useId, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client.ts'
import type { NotificationSettings, NotificationStatus, TestAlertResult } from '../api/types.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { ErrorNotice } from '../components/ErrorNotice.tsx'
import { useApi } from '../hooks/useApi.ts'
import { usePageTitle } from '../hooks/usePageTitle.ts'

export function SettingsPage() {
  usePageTitle('Alerts')
  const { api, readOnly } = useAuth()
  const settings = useApi((a) => a.getNotifications(), 'notifications')

  if (settings.loading) return <p className="empty" role="status">Loading…</p>
  if (!settings.data) return <ErrorNotice error={settings.error ?? new ApiError(0, 'Something went wrong')} onRetry={settings.reload} />
  return <SettingsForm saved={settings.data} save={(next) => api.saveNotifications(next)} test={() => api.sendTestAlert()} resend={() => api.resendVerification()} readOnly={readOnly} />
}

interface Props {
  saved: NotificationStatus
  save(settings: NotificationSettings): Promise<NotificationSettings>
  test(): Promise<TestAlertResult>
  resend(): Promise<void>
  /** The demo account: the settings are shown but cannot be changed. */
  readOnly: boolean
}

function SettingsForm({ saved, save, test, resend, readOnly }: Props) {
  const id = useId()
  const [current, setCurrent] = useState<NotificationSettings>(saved)
  const [emailAlerts, setEmailAlerts] = useState(saved.emailAlerts)
  const [chatId, setChatId] = useState(saved.telegramChatId ?? '')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError>()
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<TestAlertResult>()
  const [testError, setTestError] = useState<ApiError>()
  const [justSaved, setJustSaved] = useState(false)
  const [resending, setResending] = useState(false)
  const [resendNote, setResendNote] = useState<{ ok: boolean; text: string }>()

  const dirty = emailAlerts !== current.emailAlerts || chatId.trim() !== (current.telegramChatId ?? '')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    setJustSaved(false)
    try {
      const next = await save({ emailAlerts, telegramChatId: chatId.trim() === '' ? null : chatId.trim() })
      setCurrent(next)
      setChatId(next.telegramChatId ?? '')
      setJustSaved(true)
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
    } finally {
      setPending(false)
    }
  }

  const sendTest = async () => {
    setTesting(true)
    setTestResult(undefined)
    setTestError(undefined)
    try {
      setTestResult(await test())
    } catch (e) {
      setTestError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
    } finally {
      setTesting(false)
    }
  }

  const resendLink = async () => {
    setResending(true)
    setResendNote(undefined)
    try {
      await resend()
      setResendNote({ ok: true, text: 'Link sent. Check your inbox (and spam).' })
    } catch (e) {
      setResendNote({ ok: false, text: e instanceof ApiError ? e.message : 'Something went wrong' })
    } finally {
      setResending(false)
    }
  }

  const fieldError = error?.fieldErrors.telegramChatId
  const generalError = error && Object.keys(error.fieldErrors).length === 0 ? error.message : undefined

  return (
    <>
      <div className="page-head"><h1>Alerts</h1></div>
      <p className="muted lead">
        You are alerted when a site fails two checks in a row, when it comes back, and once when its SSL certificate has less than 14 days left.
      </p>

      {!saved.emailVerified && (
        <div className="notice" role="status">
          <span>Your email address is not confirmed yet, so email alerts are paused. Follow the link we emailed you.</span>
          <span className="form-actions">
            <button type="button" className="btn" onClick={resendLink} disabled={resending}>{resending ? 'Sending…' : 'Send the link again'}</button>
            {resendNote && <span className={resendNote.ok ? 'saved' : 'form-error'} role={resendNote.ok ? 'status' : 'alert'}>{resendNote.text}</span>}
          </span>
        </div>
      )}

      <form className="card form" onSubmit={submit} noValidate>
        <label className="check">
          <input type="checkbox" checked={emailAlerts} disabled={readOnly} onChange={(e) => { setEmailAlerts(e.target.checked); setJustSaved(false) }} />
          Email alerts to your account address
        </label>

        <div className="field">
          <label htmlFor={`${id}-chat`}>Telegram chat id</label>
          <input id={`${id}-chat`} disabled={readOnly} value={chatId} onChange={(e) => { setChatId(e.target.value); setJustSaved(false) }} inputMode="numeric" autoComplete="off" placeholder="Leave empty to turn Telegram off" aria-invalid={!!fieldError} aria-describedby={`${id}-chat-hint`} />
          {fieldError && <p className="field-error">{fieldError}</p>}
          <p id={`${id}-chat-hint`} className="field-hint">
            Open a chat with the Sitewatch bot and press Start, then paste your numeric chat id here.
          </p>
        </div>

        {generalError && <p className="form-error" role="alert">{generalError}</p>}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={pending || !dirty}>{pending ? 'Saving…' : 'Save'}</button>
          {justSaved && <span className="saved" role="status">Saved</span>}
        </div>
      </form>

      <section className="card form" aria-labelledby={`${id}-test`}>
        <h2 id={`${id}-test`}>Send a test alert</h2>
        <p className="muted">Sends a message through every channel you enabled, so you know alerts reach you.</p>
        <div className="form-actions">
          <button type="button" className="btn" onClick={sendTest} disabled={testing || dirty || readOnly}>{testing ? 'Sending…' : 'Send test alert'}</button>
          {dirty && <span className="muted small">Save your changes first.</span>}
        </div>
        {testError && <p className="form-error" role="alert">{testError.message}</p>}
        {testResult && (
          <ul className="results" aria-label="Test alert results">
            {Object.entries(testResult).map(([channel, outcome]) => (
              <li key={channel}>
                <strong className="channel">{channel}</strong> {outcome}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
