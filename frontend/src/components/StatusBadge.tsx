import type { Site } from '../api/types.ts'
import { StatusIcon } from './StatusIcon.tsx'

const LABELS = { UP: 'Up', DOWN: 'Down', UNKNOWN: 'Pending', PAUSED: 'Paused' } as const

export function StatusBadge({ site }: { site: Pick<Site, 'status' | 'active'> }) {
  const key = !site.active ? 'PAUSED' : site.status
  return (
    <span className={`badge badge-${key.toLowerCase()}`}>
      <StatusIcon status={key} />
      {LABELS[key]}
    </span>
  )
}
