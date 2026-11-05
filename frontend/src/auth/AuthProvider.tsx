import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { LoginResponse } from '../api/types'
import { AuthContext, type AuthContextValue } from './auth-context'
import { clearToken, readSession, setToken, type Session } from './token'

export function AuthProvider({ children }: { children: ReactNode }) {
  // Lazy initializer: localStorage is read once, not on every render.
  const [session, setSession] = useState<Session | null>(() => readSession())

  const login = useCallback(async (email: string, password: string) => {
    // No token is stored yet, so a 401 here reaches the page as an error
    // (the interceptor only redirects when a token already existed).
    const { data } = await api.post<LoginResponse>('/auth/login', { email, password })

    setToken(data.token)
    setSession(readSession() ?? { token: data.token, email })
  }, [])

  const register = useCallback(
    async (email: string, password: string) => {
      await api.post('/auth/register', { email, password })
      // Register returns 201 without a token: log in immediately after.
      await login(email, password)
    },
    [login],
  )

  const logout = useCallback(() => {
    clearToken()
    setSession(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      email: session?.email ?? null,
      isAuthenticated: session !== null,
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
