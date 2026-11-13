import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { Layout } from './components/Layout'
import { ProtectedRoute, PublicOnlyRoute } from './components/ProtectedRoute'
import { DocumentsPage } from './pages/DocumentsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { TodoPage } from './pages/TodoPage'

// The editor (Tiptap + ProseMirror) is the heaviest dependency in the app. Only
// visitors who open a document download it; the list page stays small.
const DocumentEditorPage = lazy(() =>
  import('./pages/DocumentEditorPage').then((module) => ({ default: module.DocumentEditorPage })),
)

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route
            path="/login"
            element={
              <PublicOnlyRoute>
                <LoginPage />
              </PublicOnlyRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicOnlyRoute>
                <RegisterPage />
              </PublicOnlyRoute>
            }
          />

          {/* Authenticated area: the guard redirects to /login before Layout renders */}
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/docs" element={<DocumentsPage />} />
            <Route
              path="/docs/:id"
              element={
                <Suspense fallback={<p className="muted">Loading editor…</p>}>
                  <DocumentEditorPage />
                </Suspense>
              }
            />
            <Route path="/todo" element={<TodoPage />} />
          </Route>

          <Route path="/" element={<Navigate to="/docs" replace />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
