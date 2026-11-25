import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { CheckTable } from '../components/CheckTable.tsx'
import { ErrorNotice } from '../components/ErrorNotice.tsx'
import { ResponseChart } from '../components/ResponseChart.tsx'
import { SiteForm } from '../components/SiteForm.tsx'
import { StatTile } from '../components/StatTile.tsx'
import { StatusBadge } from '../components/StatusBadge.tsx'
import { useApi } from '../hooks/useApi.ts'
import { usePageTitle } from '../hooks/usePageTitle.ts'
import { daysUntil, formatDate, formatMs, formatDateTime } from '../lib/format.ts'
import { average, formatUptime, percentile, responseTimes, sslState, uptimePercent } from '../lib/stats.ts'

const HOUR = 3_600_000
const RANGES = [
  { key: '1h', label: 'Last hour', ms: HOUR },
  { key: '6h', label: 'Last 6 hours', ms: 6 * HOUR },
  { key: '24h', label: 'Last 24 hours', ms: 24 * HOUR },
] as const
type RangeKey = (typeof RANGES)[number]['key']

/** The API returns at most this many checks per request. */
const HISTORY_LIMIT = 500

export function SiteDetailPage() {
  const siteId = Number(useParams().id)
  const { api, readOnly } = useAuth()
  const navigate = useNavigate()
  const [rangeKey, setRangeKey] = useState<RangeKey>('24h')
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleteError, setDeleteError] = useState<ApiError>()
  const range = RANGES.find((r) => r.key === rangeKey)!

  const site = useApi((a) => a.getSite(siteId), `site-${siteId}`, { refreshMs: 30_000 })
  const history = useApi(
    (a) => a.history(siteId, { limit: HISTORY_LIMIT, since: new Date(Date.now() - range.ms).toISOString() }),
    `history-${siteId}-${rangeKey}`,
    { refreshMs: 30_000 },
  )
  usePageTitle(site.data?.name ?? 'Site')

  const checks = history.data
  const stats = useMemo(() => {
    if (!checks) return undefined
    const times = responseTimes(checks)
    return { uptime: uptimePercent(checks), average: average(times), p95: percentile(times, 95), latest: checks[0] }
  }, [checks])

  if (!Number.isInteger(siteId) || (site.error?.status === 404)) return <NotFound />
  if (site.loading) return <p className="empty" role="status">Loading…</p>
  if (!site.data) return <ErrorNotice error={site.error ?? new ApiError(0, 'Something went wrong')} onRetry={site.reload} />
  const data = site.data

  const remove = async () => {
    try {
      await api.deleteSite(siteId)
      navigate('/', { replace: true })
    } catch (e) {
      setDeleteError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
    }
  }

  const latest = stats?.latest
  const days = latest?.sslExpiresAt ? daysUntil(latest.sslExpiresAt) : null

  return (
    <>
      <p className="breadcrumb"><Link to="/">← All sites</Link></p>
      <div className="page-head">
        <div>
          <h1>{data.name} <StatusBadge site={data} /></h1>
          <p className="muted"><a href={data.url} target="_blank" rel="noreferrer">{data.url}</a> · checked every {data.checkIntervalMinutes} min</p>
        </div>
        {!editing && !confirmingDelete && !readOnly && (
          <div className="head-actions">
            <button type="button" className="btn" onClick={() => setEditing(true)}>Edit</button>
            <button type="button" className="btn btn-danger" onClick={() => setConfirmingDelete(true)}>Delete</button>
          </div>
        )}
      </div>

      {data.status === 'DOWN' && data.active && latest && !latest.up && (
        <div className="notice notice-error" role="status">
          <span><strong>Down.</strong> {latest.error ?? 'No response'}</span>
        </div>
      )}

      {confirmingDelete && (
        <div className="card confirm" role="alertdialog" aria-labelledby="delete-title">
          <p id="delete-title"><strong>Delete {data.name}?</strong> Its check history is deleted too. This cannot be undone.</p>
          {deleteError && <p className="form-error" role="alert">{deleteError.message}</p>}
          <div className="form-actions">
            <button type="button" className="btn btn-danger-solid" onClick={remove}>Yes, delete</button>
            <button type="button" className="btn" onClick={() => { setConfirmingDelete(false); setDeleteError(undefined) }}>Cancel</button>
          </div>
        </div>
      )}

      {editing && (
        <SiteForm
          initial={data}
          submitLabel="Save changes"
          onCancel={() => setEditing(false)}
          onSubmit={async (input) => {
            await api.updateSite(siteId, input)
            setEditing(false)
            site.reload()
            history.reload()
          }}
        />
      )}

      <div className="filters" role="group" aria-label="Time range">
        {RANGES.map((r) => (
          <button key={r.key} type="button" className="chip" aria-pressed={r.key === rangeKey} onClick={() => setRangeKey(r.key)}>
            {r.label}
          </button>
        ))}
      </div>

      {history.error && !checks ? (
        <ErrorNotice error={history.error} onRetry={history.reload} />
      ) : (
        <>
          <div className="tiles">
            <StatTile hero label="Uptime" value={stats?.uptime != null ? formatUptime(stats.uptime) : '—'} hint={checks ? `${checks.length} ${checks.length === 1 ? 'check' : 'checks'}` : undefined} />
            <StatTile label="Avg response" value={stats?.average != null ? formatMs(Math.round(stats.average)) : '—'} hint={stats?.p95 != null ? `95% under ${formatMs(stats.p95)}` : undefined} />
            <StatTile
              label="SSL certificate"
              value={days === null ? '—' : sslState(days) === 'expired' ? 'Expired' : `${days} ${days === 1 ? 'day' : 'days'}`}
              hint={latest?.sslExpiresAt ? `${sslState(days!) === 'expired' ? 'Expired' : 'Expires'} ${formatDate(latest.sslExpiresAt)}` : latest ? 'Not served over HTTPS' : undefined}
            />
            <StatTile label="WordPress" value={latest?.wpVersion ?? '—'} hint={latest ? (latest.wpVersion ? 'Detected' : 'Not detected') : undefined} />
          </div>

          <section className="card chart-card" aria-labelledby="chart-title">
            <div className="card-head">
              <h2 id="chart-title">Response time <span className="muted">(ms)</span></h2>
              <div className="segmented" role="group" aria-label="View">
                <button type="button" className="chip" aria-pressed={view === 'chart'} onClick={() => setView('chart')}>Chart</button>
                <button type="button" className="chip" aria-pressed={view === 'table'} onClick={() => setView('table')}>Table</button>
              </div>
            </div>
            {checks &&
              (view === 'chart' ? (
                <ResponseChart checks={checks} rangeMs={range.ms} refreshing={history.refreshing} />
              ) : (
                <CheckTable checks={checks} />
              ))}
            {checks && checks.length >= HISTORY_LIMIT && (
              <p className="muted small">Showing the latest {HISTORY_LIMIT} checks in this range.</p>
            )}
            {latest && <p className="muted small">Last check {formatDateTime(latest.checkedAt)}</p>}
          </section>
        </>
      )}
    </>
  )
}

function NotFound() {
  return (
    <div className="card empty-card">
      <h2>Site not found</h2>
      <p>It may have been deleted, or it belongs to another account.</p>
      <Link to="/" className="btn">Back to your sites</Link>
    </div>
  )
}
