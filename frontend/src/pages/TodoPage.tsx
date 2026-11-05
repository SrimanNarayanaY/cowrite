import { useEffect, useState, type FormEvent } from 'react'

/**
 * Step 2 playground — the two hooks the rest of the app depends on.
 *
 * useState    -> owns a value that changes over time (input text, list, modal flag)
 * useEffect   -> runs side effects after render (fetch, storage, timers) and the
 *                returned function is the cleanup
 *
 * The Documents page uses exactly the same patterns at a larger scale.
 */

interface Todo {
  id: string
  text: string
}

const STORAGE_KEY = 'cowrite.playground.todos'

function loadTodos(): Todo[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []

    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []

    return parsed.filter(
      (item): item is Todo =>
        typeof item === 'object' &&
        item !== null &&
        typeof (item as Todo).id === 'string' &&
        typeof (item as Todo).text === 'string',
    )
  } catch {
    return []
  }
}

export function TodoPage() {
  // useState #1: the list itself. Passed a function so localStorage is read once.
  const [todos, setTodos] = useState<Todo[]>(loadTodos)
  // useState #2: the controlled input value.
  const [draft, setDraft] = useState('')
  const [seconds, setSeconds] = useState(0)

  // useEffect #1: persistence. Runs after every list change.
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(todos))
  }, [todos])

  // useEffect #2: a timer WITH cleanup. Without clearInterval the interval keeps
  // running after leaving the page — one leak per visit.
  useEffect(() => {
    const id = window.setInterval(() => {
      setSeconds((current) => current + 1)
    }, 1000)

    return () => window.clearInterval(id)
  }, [])

  // useEffect #3: browser side effect (the tab title), restored on unmount.
  useEffect(() => {
    const previous = document.title
    document.title = `Todo (${todos.length}) · Cowrite`

    return () => {
      document.title = previous
    }
  }, [todos.length])

  function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = draft.trim()
    if (!text) return

    // Never mutate state: build a new array.
    setTodos((current) => [...current, { id: crypto.randomUUID(), text }])
    setDraft('')
  }

  function handleDelete(id: string) {
    setTodos((current) => current.filter((todo) => todo.id !== id))
  }

  return (
    <section className="stack">
      <header className="page-head">
        <div>
          <h1>React playground</h1>
          <p className="muted">
            A todo list built with <code>useState</code> and <code>useEffect</code>, before wiring any API.
          </p>
        </div>
        <span className="badge">on this page: {seconds}s</span>
      </header>

      <div className="grid-2">
        <article className="panel">
          <h2>Live demo</h2>

          <form onSubmit={handleAdd} className="inline-form">
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="What needs to be done?"
              aria-label="New todo"
            />
            <button type="submit" className="btn btn-primary" disabled={draft.trim() === ''}>
              Add
            </button>
          </form>

          {todos.length === 0 ? (
            <p className="muted small">Nothing yet — add your first item. It survives a reload.</p>
          ) : (
            <ul className="todo-list">
              {todos.map((todo) => (
                <li key={todo.id}>
                  <span>{todo.text}</span>
                  <button
                    type="button"
                    className="btn btn-danger btn-small"
                    onClick={() => handleDelete(todo.id)}
                  >
                    Delete
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="panel">
          <h2>How it maps to the real pages</h2>

          <h3>useState — form input and modal open/close</h3>
          <pre className="code">
{`const [email, setEmail] = useState('')
const [isOpen, setIsOpen] = useState(false)

<input
  value={email}
  onChange={(e) => setEmail(e.target.value)}
/>
<button onClick={() => setIsOpen(true)}>New</button>`}
          </pre>

          <h3>useEffect — fetch on load, with cleanup</h3>
          <pre className="code">
{`useEffect(() => {
  const controller = new AbortController()

  api.get('/documents', { signal: controller.signal })
    .then((response) => setDocuments(response.data))

  return () => controller.abort()   // cleanup
}, [page])                          // only re-run when page changes`}
          </pre>

          <p className="muted small">
            The dependency array is the contract. Empty <code>[]</code> = run once. A value that the
            effect itself writes must never be in the array, or the effect re-triggers forever.
          </p>
        </article>
      </div>
    </section>
  )
}
