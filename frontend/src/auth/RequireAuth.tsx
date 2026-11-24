import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthContext.ts'

/** Route guard: signed-out visitors go to the login and come back afterwards. */
export function RequireAuth() {
  const { email } = useAuth()
  const location = useLocation()
  if (!email) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}
