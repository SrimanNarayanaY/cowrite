import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'

/**
 * Guard for authenticated routes.
 *
 * This is a UX guard, not a security boundary: anyone can flip the state in
 * devtools. Real protection is the backend rejecting requests without a valid
 * JWT. The guard only avoids flashing an empty dashboard before the redirect.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}

/** Keeps /login and /register out of reach once a session exists. */
export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return <Navigate to="/docs" replace />
  }

  return <>{children}</>
}
