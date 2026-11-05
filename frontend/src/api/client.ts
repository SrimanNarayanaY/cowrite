import axios, { type AxiosError } from 'axios'
import { clearToken, getToken } from '../auth/token'
import type { ApiErrorBody } from './types'

/**
 * One axios instance for the whole app.
 *
 * - request interceptor: attaches `Authorization: Bearer <token>` automatically,
 *   so no component ever has to remember it.
 * - response interceptor: a 401 while we thought we were logged in means the token
 *   expired or was revoked -> drop it and send the user to /login.
 *
 * The 401 rule must NOT fire for a failed login attempt (no token stored yet),
 * otherwise a simple typo would reload the page instead of showing the error.
 */
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const hadToken = Boolean(getToken())

    if (error.response?.status === 401 && hadToken) {
      clearToken()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login')
      }
    }

    return Promise.reject(error)
  },
)

/** Turns any thrown value into something worth showing a user. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0]
      if (first) return first
    }
    return body?.message ?? error.message
  }

  if (error instanceof Error) return error.message
  return 'Something went wrong'
}
