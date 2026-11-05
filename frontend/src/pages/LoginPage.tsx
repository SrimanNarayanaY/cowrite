import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'

interface RedirectState {
  from?: string
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  // useState: every keystroke updates the value the input renders.
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)

    try {
      await login(email.trim(), password)
      const from = (location.state as RedirectState | null)?.from ?? '/docs'
      navigate(from, { replace: true })
    } catch (err) {
      // 401 "Invalid credentials" from the API, or a network error
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark">C</span>
          <div>
            <h1>Welcome back</h1>
            <p className="muted">Sign in to your Cowrite documents.</p>
          </div>
        </div>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        <form onSubmit={handleSubmit} className="form" autoComplete="off">
          <label className="field">
            <span>Email</span>
            <input
              type="email"
              name="email"
              // Best effort only: browsers deliberately ignore autocomplete="off"
              // for credential fields, because users expect their password manager
              // to work. The reliable fix is per-origin: delete the saved entry or
              // open the app on 127.0.0.1 instead of localhost.
              autoComplete="off"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
            />
          </label>

          <label className="field">
            <span>Password</span>
            <input
              type="password"
              name="password"
              autoComplete="off"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
            />
          </label>

          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="muted small">
          No account? <Link to="/register">Create one</Link>
        </p>
      </div>
    </div>
  )
}
