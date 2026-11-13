import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import axios from 'axios'
import { api, errorMessage } from '../api/client'
import type { DocumentDetail } from '../api/types'
import { DocumentEditor } from '../components/DocumentEditor'

export function DocumentEditorPage() {
  const { id } = useParams<{ id: string }>()

  const [document, setDocument] = useState<DocumentDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    api
      .get<DocumentDetail>(`/documents/${id}`, { signal: controller.signal })
      .then((response) => {
        if (active) setDocument(response.data)
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
  }, [id, reloadKey])

  function reload(): void {
    setLoading(true)
    setError(null)
    setReloadKey((key) => key + 1)
  }

  if (loading && !document) {
    return <p className="muted">Loading document…</p>
  }

  if (error) {
    return (
      <div className="alert alert-row" role="alert">
        <span>{error}</span>
        <Link className="btn btn-ghost btn-small" to="/docs">
          Back to documents
        </Link>
      </div>
    )
  }

  if (!document) {
    return null
  }

  const accessText =
    document.access === 'OWNER'
      ? 'You own this document'
      : document.access === 'EDITOR'
        ? `Shared with you as editor · ${document.ownerEmail ?? 'another user'}`
        : `Read only · shared by ${document.ownerEmail ?? 'another user'}`

  return (
    <section className="stack">
      <header className="page-head">
        <div>
          <Link to="/docs" className="muted small">
            ← All documents
          </Link>
          <h1>{document.title}</h1>
          <p className="muted small">{accessText}</p>
        </div>
      </header>

      <DocumentEditor
        key={`${document.id}-${reloadKey}`}
        document={document}
        onReload={reload}
      />
    </section>
  )
}
