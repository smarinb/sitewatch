import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { Layout } from './components/Layout.tsx'
import { DashboardPage } from './pages/DashboardPage.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { SettingsPage } from './pages/SettingsPage.tsx'
import { SiteDetailPage } from './pages/SiteDetailPage.tsx'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="sites/:id" element={<SiteDetailPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<p className="empty">Page not found.</p>} />
        </Route>
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
