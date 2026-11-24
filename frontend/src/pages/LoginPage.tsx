import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { usePageTitle } from '../hooks/usePageTitle.ts'

type Mode = 'login' | 'register'

export function LoginPage() {
  const { email: signedIn, login, register } = useAuth()
  const location = useLocation()
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError>()
  usePageTitle(mode === 'login' ? 'Sign in' : 'Create account')

  if (signedIn) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/'} replace />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    try {
      await (mode === 'login' ? login : register)({ email: email.trim(), password })
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
      setPending(false)
    }
  }

  const fieldError = (field: string) => error?.fieldErrors[field]
  const generalError = error && Object.keys(error.fieldErrors).length === 0 ? error.message : undefined
  const creating = mode === 'register'

  return (
    <div className="auth">
      <div className="auth-intro">
        <img src="/favicon.svg" alt="" width="40" height="40" />
        <h1>Sitewatch</h1>
        <p>Uptime, response time, SSL expiry and WordPress version for your sites, with alerts by email and Telegram.</p>
      </div>
      <form className="card form auth-form" onSubmit={submit} noValidate>
        <h2>{creating ? 'Create your account' : 'Sign in'}</h2>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required aria-invalid={!!fieldError('email')} aria-describedby={fieldError('email') ? 'email-error' : undefined} />
          {fieldError('email') && <p id="email-error" className="field-error">{fieldError('email')}</p>}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete={creating ? 'new-password' : 'current-password'} value={password} onChange={(e) => setPassword(e.target.value)} required minLength={creating ? 8 : undefined} aria-invalid={!!fieldError('password')} aria-describedby={fieldError('password') ? 'password-error' : creating ? 'password-hint' : undefined} />
          {fieldError('password') ? <p id="password-error" className="field-error">{fieldError('password')}</p> : creating && <p id="password-hint" className="field-hint">At least 8 characters.</p>}
        </div>
        {generalError && <p className="form-error" role="alert">{generalError}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
          {pending ? 'Please wait…' : creating ? 'Create account' : 'Sign in'}
        </button>
        <p className="auth-switch">
          {creating ? 'Already have an account?' : 'New here?'}{' '}
          <button type="button" className="link-button" onClick={() => { setMode(creating ? 'login' : 'register'); setError(undefined) }}>
            {creating ? 'Sign in' : 'Create an account'}
          </button>
        </p>
      </form>
    </div>
  )
}
