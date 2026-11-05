# Cowrite — web client

React + TypeScript frontend for the Cowrite API (Spring Boot, see [`../backend`](../backend)).

## Stack

| Technology | Why |
|---|---|
| **React 19** | Component model, hooks; React Compiler enabled by the Vite template |
| **TypeScript** | DTO shapes mirrored from the backend records, so a field rename fails the build |
| **Vite** | Instant dev server + production build |
| **React Router** | `/login`, `/register`, `/docs`, protected routes |
| **Axios** | One instance with request/response interceptors (Bearer token, 401 handling) |
| **Plain CSS** | No UI framework: the layout is small enough to keep readable |

## Quick start

The API must be running first (Docker or `mvnw spring-boot:run`):

```bash
cd ../backend            # or: docker compose up -d   from the repo root
cd ../frontend
cp .env.example .env     # optional: VITE_API_URL, default is http://localhost:8080/api
npm install
npm run dev
```

Open <http://localhost:5173>.

The backend must allow this origin. It does by default:

```properties
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

If you see a CORS error in the browser console while `curl`/Postman work, that property (and `.cors(...)` on the security chain) is the place to look — never disable CORS in the browser.

## Troubleshooting

### The login form shows another project's email/password

That is the browser's password manager, not this app. Chrome/Edge store saved logins per
host and **ignore the port**, so a credential saved for another project on
`localhost:5173` is offered here too.

- Delete the saved entry (Shift + Delete on the dropdown item, or
  `chrome://password-manager/passwords`).
- Or open the app on `http://127.0.0.1:5173` instead of `http://localhost:5173` — a
  different host, so a different credential bucket. The backend already allows it.
- The inputs also set `autoComplete="off"` as a best effort, but browsers deliberately
  ignore it for credential fields.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server on port 5173 (strict port, matches the CORS allow-list) |
| `npm run build` | Type-check (`tsc -b`) + production bundle in `dist/` |
| `npm run preview` | Serve the built bundle locally |
| `npm run lint` | ESLint (React hooks + Fast Refresh rules) |
| `npm run smoke` | Playwright browser test of Milestone 1 (needs the API + dev server running; uses installed Chrome, no browser download) |

## Structure

```
src/
├── main.tsx                     # createRoot
├── App.tsx                      # routes
├── index.css                    # design tokens + layout
├── api/
│   ├── client.ts                # axios instance, interceptors, errorMessage()
│   └── types.ts                 # mirrors backend DTOs
├── auth/
│   ├── token.ts                 # localStorage read/write, JWT payload decode
│   ├── auth-context.ts          # context object + type
│   ├── AuthProvider.tsx         # session state, login/register/logout
│   └── useAuth.ts               # hook
├── components/
│   ├── Layout.tsx               # top bar, nav, logout
│   ├── ProtectedRoute.tsx       # ProtectedRoute + PublicOnlyRoute
│   └── CreateDocumentDialog.tsx # modal form (useState + cleanup)
└── pages/
    ├── LoginPage.tsx
    ├── RegisterPage.tsx
    ├── DocumentsPage.tsx        # list + create + delete + pagination
    ├── TodoPage.tsx             # Step 2: useState/useEffect playground
    └── NotFoundPage.tsx
```

## How the auth flow works

1. `POST /api/auth/login` returns `{ token }`.
2. The token is stored in `localStorage` and mirrored in `AuthProvider` state.
3. Every request goes through the axios request interceptor, which adds
   `Authorization: Bearer <token>` — components never handle headers.
4. A `401` response **while a token exists** clears it and redirects to `/login`.
   A `401` from a login attempt does not redirect; it shows "Invalid credentials".
5. `/docs` and `/todo` are wrapped in `ProtectedRoute`; `/login` and `/register`
   redirect away when already authenticated.

**Known trade-off:** `localStorage` is readable by any script on the page, so an XSS
bug equals a stolen token. Production apps usually keep the access token in memory
and the refresh token in an `HttpOnly; Secure; SameSite` cookie. For this portfolio
project the simpler choice is documented, not hidden.

The JWT payload is decoded only to show the signed-in email. That is **not**
verification — the server verifies the signature on every request.

## Step 2 — React playground (`/todo`)

The `/todo` page is the deliberate warm-up before touching the API:

- `useState` — the todo list, the controlled input, and the derived "seconds on page" value
- `useEffect` — persist to `localStorage`, and a timer **with cleanup**
  (`clearInterval`), plus a `document.title` effect that restores the previous title
- Adding uses `[...current, item]`, deleting uses `filter` — state is never mutated

The Documents page reuses the same shapes: `useState` for form + modal, `useEffect`
with an `AbortController` cleanup for fetching.

## Milestone 1 checklist

- [x] Register and log in from the browser
- [x] Document list is paginated (`Previous` / `Next`)
- [x] Create a document from a modal
- [x] Delete a document (owner only — the button is hidden for shared documents, and
      the API enforces it with 403 regardless)
- [x] A different user sees only their own documents (plus documents shared with them)
- [x] Deep link to `/docs` without a token redirects to `/login`
- [x] Expired/invalid token → automatic logout and redirect

Verified with `npm run smoke` — 10 Playwright checks in real Chrome (register, reload
with a stored session, create, cross-user isolation, delete, logout, protected route).

## What is not built yet

- Document editing UI (the API supports `PUT` for owner/editor)
- Sharing UI (the API supports `POST /api/documents/{id}/share`)
- Refresh tokens / silent renewal
- Optimistic updates (the list refetches after create/delete)

These are the natural next steps before real-time collaboration.
