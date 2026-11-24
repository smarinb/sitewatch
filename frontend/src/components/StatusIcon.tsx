import type { SiteStatus } from '../api/types.ts'

/** Shape plus color: status is never carried by color alone. */
export function StatusIcon({ status, size = 14 }: { status: SiteStatus | 'PAUSED' | 'WARNING'; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 16 16', 'aria-hidden': true, focusable: false } as const
  switch (status) {
    case 'UP':
      return (
        <svg {...common} className="icon icon-up">
          <circle cx="8" cy="8" r="7" fill="currentColor" />
          <path d="M4.6 8.3l2.3 2.3 4.5-4.8" fill="none" stroke="var(--on-status)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'DOWN':
      return (
        <svg {...common} className="icon icon-down">
          <path d="M8 1.2l7 12.6H1z" fill="currentColor" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
          <path d="M8 6v3.6M8 11.6v.1" stroke="var(--on-status)" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'WARNING':
      return (
        <svg {...common} className="icon icon-warning">
          <path d="M8 1.2l7 12.6H1z" fill="currentColor" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
          <path d="M8 6v3.6M8 11.6v.1" stroke="var(--on-warning)" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'PAUSED':
      return (
        <svg {...common} className="icon icon-muted">
          <circle cx="8" cy="8" r="6.2" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <path d="M6.3 5.6v4.8M9.7 5.6v4.8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      )
    default:
      return (
        <svg {...common} className="icon icon-muted">
          <circle cx="8" cy="8" r="6.2" fill="none" stroke="currentColor" strokeWidth="1.6" strokeDasharray="2.6 2.2" />
        </svg>
      )
  }
}
