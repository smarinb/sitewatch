import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { usePageTitle } from '../hooks/usePageTitle.ts'

type State = { status: 'working' } | { status: 'done' } | { status: 'failed'; message: string }

/** Where the link in the confirmation email lands. Public: the email is opened with or without a session. */
export function VerifyEmailPage() {
  usePageTitle('Confirm your email')
  const { api, email: signedIn } = useAuth()
  const token = useSearchParams()[0].get('token')
  const [state, setState] = useState<State>(
    token ? { status: 'working' } : { status: 'failed', message: 'This link is incomplete. Open it again from the email.' },
  )
  // Strict Mode runs effects twice in development; the token works once, so ask only once.
  const asked = useRef(false)

  useEffect(() => {
    if (!token || asked.current) return
    asked.current = true
    api.verifyEmail(token).then(
      () => setState({ status: 'done' }),
      (e: unknown) => setState({ status: 'failed', message: e instanceof ApiError ? e.message : 'Something went wrong' }),
    )
  }, [api, token])

  return (
    <div className="auth">
      <div className="auth-intro">
        <img src="/favicon.svg" alt="" width="40" height="40" />
        <h1>Sitewatch</h1>
      </div>
      <div className="card form auth-form" role="status" aria-live="polite">
        {state.status === 'working' && <p>Confirming your email address…</p>}
        {state.status === 'done' && (
          <>
            <h2>Email confirmed</h2>
            <p className="muted">You will now get alert emails for your sites.</p>
            <Link className="btn btn-primary btn-block" to={signedIn ? '/' : '/login'}>
              {signedIn ? 'Go to your sites' : 'Sign in'}
            </Link>
          </>
        )}
        {state.status === 'failed' && (
          <>
            <h2>We could not confirm your email</h2>
            <p className="form-error">{state.message}</p>
            <p className="muted">Sign in and open Alerts to get a new link.</p>
            <Link className="btn btn-block" to={signedIn ? '/settings' : '/login'}>
              {signedIn ? 'Go to Alerts' : 'Sign in'}
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
