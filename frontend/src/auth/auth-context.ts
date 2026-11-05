import { createContext } from 'react'

export interface AuthContextValue {
  /** Email decoded from the JWT, for display only. */
  email: string | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

/**
 * Kept in its own module so the provider file exports only a component and the
 * hook file exports only a hook (React Fast Refresh requires that split).
 */
export const AuthContext = createContext<AuthContextValue | null>(null)
