import { useEffect, useState } from 'react'
import axios from 'axios'
import { api, errorMessage } from '../api/client'
import type { DocumentSummary, PageResponse } from '../api/types'
import { useAuth } from '../auth/useAuth'
import { CreateDocumentDialog } from '../components/CreateDocumentDialog'

const PAGE_SIZE = 5

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function DocumentsPage() {
  const { email } = useAuth()

  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [data, setData] = useState<PageResponse<DocumentSummary> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  // useEffect: fetch whenever the page (or an explicit reload) changes.
  // The AbortController is the cleanup: if the user clicks "next" twice quickly,
  // the older request is cancelled so a slow response cannot overwrite the new one.
  // Leaving out the dependency array, or setting state that is also a dependency,
  // is what produces the classic infinite request loop.
  //
  // Note: no setState synchronously in the effect body. Loading/error flags are set
  // by the event handlers that trigger a new request, so the effect only starts I/O
  // and writes state from async callbacks.
  useEffect(() => {
    const controller = new AbortController()
    let active = true

    api
      .get<PageResponse<DocumentSummary>>('/documents', {
        params: { page, size: PAGE_SIZE },
        signal: controller.signal,
      })
      .then((response) => {
        if (active) setData(response.data)
      })
      .catch((err: unknown) => {
        if (!active || axios.isCancel(err)) return
        setError(errorMessage(err))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [page, reloadKey])

  function reload() {
    setLoading(true)
    setError(null)
    setReloadKey((key) => key + 1)
  }

  function goToPage(nextPage: number) {
    setLoading(true)
    setError(null)
    setPage(nextPage)
  }

  async function handleDelete(document: DocumentSummary) {
    if (!window.confirm(`Delete "${document.title}"? This cannot be undone.`)) {
      return
    }

    setDeletingId(document.id)
    setError(null)

    try {
      await api.delete(`/documents/${document.id}`)

      // Deleting the last row of a page would leave the user on an empty page.
      if (data && data.content.length === 1 && !data.first) {
        goToPage(Math.max(0, data.page - 1))
      } else {
        reload()
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setDeletingId(null)
    }
  }

  const documents = data?.content ?? []

  return (
    <section className="stack">
      <header className="page-head">
        <div>
          <h1>Your documents</h1>
          <p className="muted">
            Owned by you and shared with you. {data ? `${data.totalElements} total` : ''}
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
          New document
        </button>
      </header>

      {error && (
        <div className="alert alert-row" role="alert">
          <span>{error}</span>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Retry
          </button>
        </div>
      )}

      {loading && documents.length === 0 && <p className="muted">Loading documents…</p>}

      {!loading && documents.length === 0 && !error && (
        <div className="empty-state">
          <h2>No documents yet</h2>
          <p className="muted">Create your first document, or ask a teammate to share one with you.</p>
          <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            Create a document
          </button>
        </div>
      )}

      {documents.length > 0 && (
        <ul className="doc-grid">
          {documents.map((document) => {
            const mine = document.ownerEmail !== null && document.ownerEmail === email

            return (
              <li key={document.id} className={loading ? 'doc-card busy' : 'doc-card'}>
                <div className="doc-card-head">
                  <h2>{document.title}</h2>
                  <span className={mine ? 'badge badge-own' : 'badge'}>
                    {mine ? 'Yours' : `Shared by ${document.ownerEmail ?? 'someone'}`}
                  </span>
                </div>

                {document.content ? (
                  <details className="doc-content">
                    <summary>Preview content</summary>
                    <p>{document.content}</p>
                  </details>
                ) : (
                  <p className="muted small">No content yet.</p>
                )}

                <footer className="doc-card-foot">
                  <span className="muted small">Updated {formatDate(document.updatedAt)}</span>
                  {mine && (
                    <button
                      type="button"
                      className="btn btn-danger btn-small"
                      onClick={() => handleDelete(document)}
                      disabled={deletingId === document.id}
                    >
                      {deletingId === document.id ? 'Deleting…' : 'Delete'}
                    </button>
                  )}
                </footer>
              </li>
            )
          })}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <nav className="pager" aria-label="Pagination">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => goToPage(data.page - 1)}
            disabled={data.first || loading}
          >
            ← Previous
          </button>
          <span className="muted">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => goToPage(data.page + 1)}
            disabled={data.last || loading}
          >
            Next →
          </button>
        </nav>
      )}

      {createOpen && (
        <CreateDocumentDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            if (page === 0) {
              reload()
            } else {
              goToPage(0)
            }
          }}
        />
      )}
    </section>
  )
}
