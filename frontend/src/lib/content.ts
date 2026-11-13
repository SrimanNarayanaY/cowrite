import type { JSONContent } from '@tiptap/react'

/**
 * Documents are stored as the opaque string the editor produced (Tiptap JSON).
 *
 * Older documents in this project were plain text, so loading tries JSON first and
 * falls back to wrapping each line in a paragraph. That keeps old rows readable
 * instead of rendering raw JSON or losing the text.
 */
export function parseEditorContent(raw: string | null): JSONContent | string {
  if (!raw) return ''

  try {
    const parsed: unknown = JSON.parse(raw)
    if (isTiptapDoc(parsed)) return parsed
  } catch {
    // Not JSON: a plain-text document from before the editor existed.
  }

  return {
    type: 'doc',
    content: raw.split('\n').map((line) =>
      line
        ? { type: 'paragraph', content: [{ type: 'text', text: line }] }
        : { type: 'paragraph' },
    ),
  }
}

/** Short plain-text version for list previews. */
export function plainText(raw: string | null): string {
  const content = parseEditorContent(raw)
  if (typeof content === 'string') return content
  return textOf(content).replace(/\s+/g, ' ').trim()
}

function textOf(node: JSONContent): string {
  const own = typeof node.text === 'string' ? node.text : ''
  const children = Array.isArray(node.content) ? node.content.map(textOf) : []
  return [own, ...children].filter(Boolean).join(' ')
}

function isTiptapDoc(value: unknown): value is JSONContent {
  return typeof value === 'object' && value !== null && (value as { type?: unknown }).type === 'doc'
}
