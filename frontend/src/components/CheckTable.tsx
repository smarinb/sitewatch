import type { Check } from '../api/types.ts'
import { formatDateTime, formatMs } from '../lib/format.ts'
import { StatusIcon } from './StatusIcon.tsx'

/** The chart's table twin: every value the chart shows, readable without a pointer. */
export function CheckTable({ checks }: { checks: Check[] }) {
  if (checks.length === 0) return <p className="empty">No checks in this time range yet.</p>
  return (
    <div className="table-scroll table-scroll-tall" tabIndex={0} role="region" aria-label="Check history table">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Time</th>
            <th scope="col">Result</th>
            <th scope="col">HTTP</th>
            <th scope="col" className="num">Response</th>
            <th scope="col">Details</th>
          </tr>
        </thead>
        <tbody>
          {checks.map((check) => (
            <tr key={check.checkedAt}>
              <td>{formatDateTime(check.checkedAt)}</td>
              <td>
                <span className="status-text">
                  <StatusIcon status={check.up ? 'UP' : 'DOWN'} />
                  {check.up ? 'Up' : 'Down'}
                </span>
              </td>
              <td>{check.httpStatus ?? '—'}</td>
              <td className="num">{check.responseTimeMs === null ? '—' : formatMs(check.responseTimeMs)}</td>
              <td className="muted">{check.error ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
