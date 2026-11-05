import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="auth-screen">
      <div className="auth-card centered">
        <span className="brand-mark">C</span>
        <h1>Page not found</h1>
        <p className="muted">That route does not exist.</p>
        <Link to="/docs" className="btn btn-primary">
          Back to documents
        </Link>
      </div>
    </div>
  )
}
