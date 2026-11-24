import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App.tsx'
import { AuthProvider } from '../auth/AuthProvider.tsx'

export function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  )
}
