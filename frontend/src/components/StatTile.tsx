import type { ReactNode } from 'react'

interface Props {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** The one number a view leads with. */
  hero?: boolean
}

export function StatTile({ label, value, hint, hero = false }: Props) {
  return (
    <div className={hero ? 'tile tile-hero' : 'tile'}>
      <div className="tile-label">{label}</div>
      <div className="tile-value">{value}</div>
      {hint ? <div className="tile-hint">{hint}</div> : null}
    </div>
  )
}
