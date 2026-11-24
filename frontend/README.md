# Sitewatch: panel web

React 19 + TypeScript + Vite. Se despliega como sitio estático en Cloudflare Pages.

```bash
npm install
npm run dev        # http://localhost:5173 (Vite hace de proxy de /api hacia localhost:8080)
npm test           # Vitest + Testing Library
npm run lint       # oxlint
npm run build      # tsc + vite build → dist/
```

## Estructura
| Carpeta | Contenido |
|---|---|
| `src/api` | Cliente tipado de la API (`createApi`), errores `ApiError` (problem details) y tipos |
| `src/auth` | Sesión JWT en `localStorage`, `AuthProvider`, guarda de rutas. Si la API responde 401, cierra la sesión |
| `src/hooks` | `useApi`: carga datos, refresca cada 30 s y conserva la vista anterior mientras recarga |
| `src/lib` | Funciones puras: escalas y segmentos de la gráfica, formatos, estadísticas (uptime, p95) |
| `src/components` | `ResponseChart` (SVG propio, sin librería de gráficas), tabla equivalente, formulario de sitios… |
| `src/pages` | Login/registro, lista de sitios, detalle con histórico y ajustes de alertas |

## Decisiones
- **Gráfica en SVG a mano** en lugar de una librería: son ~200 líneas, pesa 0 KB extra y permite cursor con tooltip, teclado (flechas, Inicio, Fin, Esc) y vista de tabla con los mismos datos. Una caída continua se dibuja como una banda, no como un marcador por chequeo.
- **El estado nunca va solo en color:** cada estado lleva icono y texto (Up, Down, Pending, Paused; certificado por caducar).
- **Tema claro y oscuro** automático (`prefers-color-scheme`), con la paleta de la gráfica validada para ambos.
- **Token JWT en `localStorage`.** Es la opción simple para una SPA estática en otro dominio que la API, y no hay cookies que proteger de CSRF. El riesgo es el XSS, por eso no se usa `dangerouslySetInnerHTML` y React escapa todo. El token caduca a las 24 h y no hay refresh: al caducar se vuelve al login.

## Variables de entorno
| Variable | Uso |
|---|---|
| `VITE_API_URL` | URL base de la API en producción (`https://monitor-api.sergiomarin.dev`). En desarrollo se deja vacía: el proxy de Vite evita CORS |

## Cloudflare Pages
Directorio raíz `frontend`, comando `npm run build`, salida `dist`, y `VITE_API_URL` como variable de entorno. `public/_redirects` reenvía las rutas del cliente (`/sites/3`) a `index.html`. En la API hay que poner `CORS_ALLOWED_ORIGINS=https://monitor.sergiomarin.dev`.
