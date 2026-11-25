# Sitewatch: web panel

React 19 + TypeScript + Vite. Deployed as a static site on Cloudflare Pages.

```bash
npm install
npm run dev        # http://localhost:5173 (Vite proxies /api to localhost:8080)
npm test           # Vitest + Testing Library
npm run lint       # oxlint
npm run build      # tsc + vite build → dist/
```

## Structure
| Folder | Contents |
|---|---|
| `src/api` | Typed API client (`createApi`), `ApiError` (problem details) and types |
| `src/auth` | JWT session in `localStorage`, `AuthProvider`, route guard. On a 401 from the API it signs the user out |
| `src/hooks` | `useApi`: loads data, refreshes every 30 s and keeps the previous view while reloading |
| `src/lib` | Pure functions: chart scales and segments, formatting, statistics (uptime, p95) |
| `src/components` | `ResponseChart` (own SVG, no chart library), the equivalent table, the site form… |
| `src/pages` | Login, site list, detail with history, alert settings, and the page the email confirmation link opens |

## Decisions
- **A hand-drawn SVG chart** instead of a library: it is ~200 lines, adds 0 KB, and supports a cursor with tooltip, keyboard control (arrows, Home, End, Esc) and a table view with the same data. A continuous outage is drawn as a band, not as one marker per check.
- **State is never only a colour:** every state has an icon and text (Up, Down, Pending, Paused; certificate about to expire).
- **Automatic light and dark theme** (`prefers-color-scheme`), with the chart palette validated for both.
- **JWT in `localStorage`.** It is the simple option for a static SPA on a different domain from the API, and there are no cookies to protect from CSRF. The risk is XSS, which is why `dangerouslySetInnerHTML` is never used and React escapes everything. The token expires after 24 h and there is no refresh: when it expires the user goes back to the login.
- **The panel asks the server what to offer.** The login page reads `GET /api/auth/config`: on the public demo it hides sign-up and shows "Try the demo".
- **Read-only demo mode.** Tokens of the demo account carry a `readOnly` claim; the panel then shows a banner and hides every control that changes data. The server enforces it too (it answers `403` to any write), so the UI is a convenience and never the protection.

## Environment variables
| Variable | Use |
|---|---|
| `VITE_API_URL` | Base URL of the API in production (`https://monitor-api.sergiomarin.dev`). Leave it empty in development: the Vite proxy avoids CORS |

## Cloudflare Pages
Root directory `frontend`, command `npm run build`, output `dist`, and `VITE_API_URL` as an environment variable. `public/_redirects` forwards client routes (`/sites/3`) to `index.html`. On the API, set `CORS_ALLOWED_ORIGINS=https://monitor.sergiomarin.dev`.
