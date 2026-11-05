import { useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { DocumentSummary } from '../api/types'

interface CreateDocumentDialogProps {
  onClose: () => void
  onCreated: () => void
}

export function CreateDocumentDialog({ onClose, onCreated }: CreateDocumentDialogProps) {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // useEffect with cleanup: the keydown listener must be removed when the modal
  // closes, otherwise every open/close cycle leaks another listener.
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSaving(true)

    try {
      await api.post<DocumentSummary>('/documents', {
        title: title.trim(),
        content: content.trim() === '' ? null : content,
      })
      onCreated()
      onClose()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="overlay" onMouseDown={onClose}>
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-document-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 id="create-document-title">New document</h2>
        <p className="muted small">The owner is taken from your token — it cannot be set here.</p>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        <form onSubmit={handleSubmit} className="form">
          <label className="field">
            <span>Title</span>
            <input
              autoFocus
              required
              maxLength={200}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Design notes"
            />
          </label>

          <label className="field">
            <span>Content (optional)</span>
            <textarea
              rows={6}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="Start writing…"
            />
          </label>

          <div className="dialog-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Creating…' : 'Create document'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
