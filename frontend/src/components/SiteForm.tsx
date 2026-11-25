import { useId, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client.ts'
import type { Site, SiteInput } from '../api/types.ts'
import { normalizeUrl } from '../lib/url.ts'

const INTERVALS = [
  { minutes: 5, label: 'Every 5 minutes' },
  { minutes: 10, label: 'Every 10 minutes' },
  { minutes: 15, label: 'Every 15 minutes' },
  { minutes: 30, label: 'Every 30 minutes' },
  { minutes: 60, label: 'Every hour' },
  { minutes: 360, label: 'Every 6 hours' },
  { minutes: 1440, label: 'Once a day' },
]

interface Props {
  /** Present when editing; the form then also offers pausing the site. */
  initial?: Site
  submitLabel: string
  onSubmit(input: SiteInput): Promise<void>
  onCancel(): void
}

export function SiteForm({ initial, submitLabel, onSubmit, onCancel }: Props) {
  const id = useId()
  const [name, setName] = useState(initial?.name ?? '')
  const [url, setUrl] = useState(initial?.url ?? '')
  const [intervalMinutes, setIntervalMinutes] = useState(initial?.checkIntervalMinutes ?? 5)
  const [active, setActive] = useState(initial?.active ?? true)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError>()

  const intervals = INTERVALS.some((o) => o.minutes === intervalMinutes)
    ? INTERVALS
    : [...INTERVALS, { minutes: intervalMinutes, label: `Every ${intervalMinutes} minutes` }].sort((a, b) => a.minutes - b.minutes)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    try {
      await onSubmit({ name: name.trim(), url: normalizeUrl(url), checkIntervalMinutes: intervalMinutes, active })
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
      setPending(false)
    }
  }

  const fieldError = (field: string) => error?.fieldErrors[field]
  const generalError = error && Object.keys(error.fieldErrors).length === 0 ? error.message : undefined

  return (
    <form className="card form" onSubmit={submit} noValidate>
      <div className="form-grid">
        <div className="field">
          <label htmlFor={`${id}-name`}>Name</label>
          <input id={`${id}-name`} value={name} onChange={(e) => setName(e.target.value)} placeholder="My blog" maxLength={120} required aria-invalid={!!fieldError('name')} aria-describedby={fieldError('name') ? `${id}-name-error` : undefined} />
          {fieldError('name') && <p id={`${id}-name-error`} className="field-error">{fieldError('name')}</p>}
        </div>
        <div className="field">
          <label htmlFor={`${id}-url`}>URL</label>
          <input id={`${id}-url`} value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com" inputMode="url" autoCapitalize="none" spellCheck={false} required aria-invalid={!!fieldError('url')} aria-describedby={fieldError('url') ? `${id}-url-error` : undefined} />
          {fieldError('url') && <p id={`${id}-url-error`} className="field-error">{fieldError('url')}</p>}
        </div>
        <div className="field">
          <label htmlFor={`${id}-interval`}>Check</label>
          <select id={`${id}-interval`} value={intervalMinutes} onChange={(e) => setIntervalMinutes(Number(e.target.value))}>
            {intervals.map((o) => (
              <option key={o.minutes} value={o.minutes}>{o.label}</option>
            ))}
          </select>
        </div>
      </div>
      {initial && (
        <label className="check">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Monitoring active (uncheck to pause)
        </label>
      )}
      {generalError && <p className="form-error" role="alert">{generalError}</p>}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={pending}>{pending ? 'Saving…' : submitLabel}</button>
        <button type="button" className="btn" onClick={onCancel} disabled={pending}>Cancel</button>
      </div>
    </form>
  )
}
