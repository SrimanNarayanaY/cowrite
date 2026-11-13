import { useCallback, useEffect, useRef, useState } from 'react'
import { EditorContent, useEditor, type JSONContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import axios from 'axios'
import { api, errorMessage } from '../api/client'
import type { DocumentDetail } from '../api/types'
import { parseEditorContent } from '../lib/content'

type SaveStatus = 'idle' | 'saving' | 'saved' | 'conflict' | 'error'

/** How long the user must pause before the editor saves. */
const DEBOUNCE_MS = 1000

interface DocumentEditorProps {
  document: DocumentDetail
  onReload: () => void
}

/**
 * Tiptap editor with debounced auto-save.
 *
 * Two concurrency rules keep saves correct:
 * 1. One request in flight at a time. While it runs, new edits replace a single
 *    "queued" payload instead of starting a second request, so responses cannot
 *    arrive out of order.
 * 2. The version returned by the server is stored and sent with the next save.
 *    A stale version gets 409, and the editor stops saving until the user reloads,
 *    instead of retrying the same rejected payload forever.
 */
export function DocumentEditor({ document, onReload }: DocumentEditorProps) {
  const [status, setStatus] = useState<SaveStatus>('idle')
  const [message, setMessage] = useState('')
  const [version, setVersion] = useState(document.version)

  const versionRef = useRef(document.version)
  const timerRef = useRef<number | null>(null)
  const inFlightRef = useRef(false)
  const queuedRef = useRef<JSONContent | null>(null)
  const conflictRef = useRef(false)

  const persist = useCallback(
    async (first: JSONContent) => {
      if (inFlightRef.current) {
        // Keep only the newest pending edit; older ones are already superseded.
        queuedRef.current = first
        return
      }

      inFlightRef.current = true
      let content: JSONContent | null = first

      while (content) {
        setStatus('saving')
        setMessage('')

        try {
          const { data } = await api.put<{ version: number }>(
            `/documents/${document.id}/content`,
            {
              content: JSON.stringify(content),
              version: versionRef.current,
            },
          )

          versionRef.current = data.version
          setVersion(data.version)
          setStatus('saved')

          content = queuedRef.current
          queuedRef.current = null
        } catch (err) {
          inFlightRef.current = false
          queuedRef.current = null

          if (axios.isAxiosError(err) && err.response?.status === 409) {
            conflictRef.current = true
            setStatus('conflict')
            setMessage('Document changed elsewhere. Reload to see the latest version.')
            return
          }

          setStatus('error')
          setMessage(errorMessage(err))
          return
        }
      }

      inFlightRef.current = false
    },
    [document.id],
  )

  const scheduleSave = useCallback(
    (content: JSONContent) => {
      if (!document.canEdit || conflictRef.current) return

      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }

      // Debounce: a burst of keystrokes produces one request, not one per key.
      timerRef.current = window.setTimeout(() => {
        timerRef.current = null
        void persist(content)
      }, DEBOUNCE_MS)
    },
    [document.canEdit, persist],
  )

  const editor = useEditor({
    extensions: [StarterKit],
    content: parseEditorContent(document.content),
    editable: document.canEdit,
    immediatelyRender: false,
    shouldRerenderOnTransaction: true,
    onUpdate: ({ editor: current }) => {
      scheduleSave(current.getJSON())
    },
  })

  // Cancel a pending debounce when the user leaves the page.
  useEffect(() => {
    return () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
    }
  }, [])

  if (!editor) {
    return <p className="muted">Loading editor…</p>
  }

  const canEdit = document.canEdit

  function toolbarClass(active: boolean): string {
    return active ? 'btn btn-small btn-toolbar btn-active' : 'btn btn-small btn-toolbar btn-ghost'
  }

  const statusText =
    status === 'saving'
      ? 'Saving…'
      : status === 'saved'
        ? 'Saved'
        : status === 'error'
          ? 'Not saved'
          : status === 'conflict'
            ? 'Not saved'
            : canEdit
              ? 'Changes save automatically'
              : 'Read only'

  return (
    <div className="editor-shell">
      <div className="editor-toolbar" role="toolbar" aria-label="Formatting">
        <button
          type="button"
          className={toolbarClass(editor.isActive('bold'))}
          onClick={() => editor.chain().focus().toggleBold().run()}
          disabled={!canEdit}
          aria-pressed={editor.isActive('bold')}
        >
          Bold
        </button>
        <button
          type="button"
          className={toolbarClass(editor.isActive('italic'))}
          onClick={() => editor.chain().focus().toggleItalic().run()}
          disabled={!canEdit}
          aria-pressed={editor.isActive('italic')}
        >
          Italic
        </button>
        <button
          type="button"
          className={toolbarClass(editor.isActive('heading', { level: 1 }))}
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
          disabled={!canEdit}
          aria-pressed={editor.isActive('heading', { level: 1 })}
        >
          H1
        </button>
        <button
          type="button"
          className={toolbarClass(editor.isActive('heading', { level: 2 }))}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          disabled={!canEdit}
          aria-pressed={editor.isActive('heading', { level: 2 })}
        >
          H2
        </button>

        <span className="toolbar-spacer" />
        <span className="muted small">v{version}</span>
      </div>

      {status === 'conflict' && (
        <div className="alert alert-row editor-banner" role="alert">
          <span>{message}</span>
          <button type="button" className="btn btn-ghost btn-small" onClick={onReload}>
            Reload
          </button>
        </div>
      )}

      {status === 'error' && (
        <p className="alert editor-banner" role="alert">
          {message}
        </p>
      )}

      <EditorContent editor={editor} className="editor-body" />

      <footer className="editor-footer">
        <span
          className={status === 'saving' ? 'editor-status saving' : status === 'saved' ? 'editor-status saved' : status === 'idle' ? 'editor-status' : 'editor-status error'}
          role="status"
          aria-live="polite"
        >
          {statusText}
        </span>
        {!canEdit && <span className="badge">View only</span>}
      </footer>
    </div>
  )
}
