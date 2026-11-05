import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function Layout() {
  const { email, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/docs" className="brand">
            <span className="brand-mark">C</span>
            Cowrite
          </NavLink>

          <nav className="nav">
            <NavLink to="/docs" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Documents
            </NavLink>
            <NavLink to="/todo" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              React playground
            </NavLink>
          </nav>

          <div className="topbar-user">
            <span className="user-email" title={email ?? ''}>
              {email ?? 'signed in'}
            </span>
            <button type="button" className="btn btn-ghost" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="page">
        <Outlet />
      </main>

      <footer className="footer">
        Cowrite API · <a href="http://localhost:8080/api/documents">localhost:8080</a>
      </footer>
    </div>
  )
}
