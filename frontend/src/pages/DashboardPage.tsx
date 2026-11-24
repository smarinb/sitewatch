import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import type { LatestCheck, Site } from '../api/types.ts'
import { ErrorNotice } from '../components/ErrorNotice.tsx'
import { SiteForm } from '../components/SiteForm.tsx'
import { StatTile } from '../components/StatTile.tsx'
import { StatusBadge } from '../components/StatusBadge.tsx'
import { StatusIcon } from '../components/StatusIcon.tsx'
import { useApi } from '../hooks/useApi.ts'
import { usePageTitle } from '../hooks/usePageTitle.ts'
import { daysUntil, formatDate, formatMs, hostname, relativeTime } from '../lib/format.ts'
import { sslState } from '../lib/stats.ts'
import { useAuth } from '../auth/AuthContext.ts'

const REFRESH_MS = 30_000

export function DashboardPage() {
  const { api } = useAuth()
  const sites = useApi((a) => a.listSites(), 'sites', { refreshMs: REFRESH_MS })
  const latest = useApi((a) => a.latestChecks(), 'latest-checks', { refreshMs: REFRESH_MS })
  const [adding, setAdding] = useState(false)
  usePageTitle('Sites')

  const latestBySite = useMemo(() => new Map(latest.data?.map((c) => [c.siteId, c])), [latest.data])
  const list = sites.data ?? []
  const counts = {
    up: list.filter((s) => s.active && s.status === 'UP').length,
    down: list.filter((s) => s.active && s.status === 'DOWN').length,
  }

  if (sites.loading) return <p className="empty" role="status">Loading your sites…</p>
  if (sites.error && !sites.data) return <ErrorNotice error={sites.error} onRetry={sites.reload} />

  return (
    <>
      <div className="page-head">
        <h1>Sites</h1>
        {!adding && <button type="button" className="btn btn-primary" onClick={() => setAdding(true)}>Add site</button>}
      </div>

      {adding && (
        <SiteForm
          submitLabel="Add site"
          onCancel={() => setAdding(false)}
          onSubmit={async (input) => {
            await api.createSite(input)
            setAdding(false)
            sites.reload()
            latest.reload()
          }}
        />
      )}

      {list.length > 0 && (
        <div className="tiles">
          <StatTile label="Sites" value={list.length} />
          <StatTile label="Up" value={counts.up} />
          <StatTile label="Down" value={counts.down} />
        </div>
      )}

      {sites.error && <ErrorNotice error={sites.error} onRetry={sites.reload} />}

      {list.length === 0 ? (
        !adding && (
          <div className="card empty-card">
            <h2>No sites yet</h2>
            <p>Add a site and Sitewatch starts checking it within a minute. You get an alert if it goes down.</p>
            <button type="button" className="btn btn-primary" onClick={() => setAdding(true)}>Add your first site</button>
          </div>
        )
      ) : (
        <div className={sites.refreshing ? 'card table-card is-refreshing' : 'card table-card'}>
          <div className="table-scroll" tabIndex={0} role="region" aria-label="Monitored sites">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Site</th>
                  <th scope="col">Status</th>
                  <th scope="col" className="num">Response</th>
                  <th scope="col">SSL certificate</th>
                  <th scope="col">WordPress</th>
                  <th scope="col">Last check</th>
                </tr>
              </thead>
              <tbody>
                {list.map((site) => (
                  <SiteRow key={site.id} site={site} check={latestBySite.get(site.id)} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  )
}

function SiteRow({ site, check }: { site: Site; check: LatestCheck | undefined }) {
  return (
    <tr>
      <td>
        <Link to={`/sites/${site.id}`} className="site-link">{site.name}</Link>
        <div className="muted small">{hostname(site.url)}</div>
      </td>
      <td><StatusBadge site={site} /></td>
      <td className="num">{check?.up && check.responseTimeMs !== null ? formatMs(check.responseTimeMs) : '—'}</td>
      <td><SslCell expiresAt={check?.sslExpiresAt ?? null} /></td>
      <td>{check?.wpVersion ?? '—'}</td>
      <td className="muted" title={site.lastCheckedAt ?? undefined}>
        {site.lastCheckedAt ? relativeTime(site.lastCheckedAt) : 'Not yet'}
      </td>
    </tr>
  )
}

function SslCell({ expiresAt }: { expiresAt: string | null }) {
  if (!expiresAt) return <span className="muted">—</span>
  const days = daysUntil(expiresAt)
  const state = sslState(days)
  const text = state === 'expired' ? 'Expired' : days === 0 ? 'Expires today' : `${days} ${days === 1 ? 'day' : 'days'} left`
  return (
    <span className="status-text" title={`Expires ${formatDate(expiresAt)}`}>
      {state !== 'ok' && <StatusIcon status={state === 'expired' ? 'DOWN' : 'WARNING'} />}
      {text}
    </span>
  )
}
