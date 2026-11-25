import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext.ts'

export function Layout() {
  const { email, readOnly, logout } = useAuth()
  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="container topbar-inner">
          <NavLink to="/" className="brand" end>
            <img src="/favicon.svg" alt="" width="22" height="22" />
            Sitewatch
          </NavLink>
          <nav className="nav" aria-label="Main">
            <NavLink to="/" end>Sites</NavLink>
            <NavLink to="/settings">Alerts</NavLink>
          </nav>
          <div className="topbar-user">
            <span className="muted user-email" title={email ?? undefined}>{email}</span>
            <button type="button" className="btn btn-small" onClick={logout}>Sign out</button>
          </div>
        </div>
      </header>
      <main id="main" className="container main">
        {readOnly && (
          <div className="notice" role="status">
            <span><strong>Read-only demo.</strong> You can look around; changes are turned off.</span>
          </div>
        )}
        <Outlet />
      </main>
      <footer className="container footer">
        <a href="https://github.com/smarinb/sitewatch" target="_blank" rel="noreferrer">Source on GitHub</a>
      </footer>
    </>
  )
}
