# Cowrite API

> Backend for a collaborative document editor — Spring Boot 4, PostgreSQL, JWT security, role-based sharing, package-by-feature architecture.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-supported-blue)
![Docker](https://img.shields.io/badge/docker%20compose-ready-blue)
![Tests](https://img.shields.io/badge/tests-47%20passing-success)

---

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Tech stack & why](#tech-stack--why)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Authentication flow](#authentication-flow)
- [Permissions & roles](#permissions--roles)
- [API reference](#api-reference)
- [Error format](#error-format)
- [Pagination](#pagination)
- [Performance: the N+1 story](#performance-the-n1-story)
- [Testing](#testing)
- [Getting started](#getting-started)
- [Running with Docker](#running-with-docker)
- [Frontend (React)](#frontend-react)
- [Configuration](#configuration)
- [Design decisions](#design-decisions)
- [Roadmap](#roadmap)

---

## Overview

Cowrite is a collaborative document editor. This repository is its **REST API**: users register, log in, own documents, and share them with other people as `VIEWER` or `EDITOR`.

The codebase is organised **by feature** (not by layer), so a feature is one folder: entity, repository, service, controller, DTOs and exceptions live together. Adding a feature means adding one package, not touching four `controller/`, `service/`, `repository/`, `dto/` folders.

**Current status:** authentication, document CRUD, and role-based sharing are complete and covered by integration tests. Real-time collaboration (WebSocket/CRDT) is on the roadmap.

---

## Features

- Email/password registration with BCrypt password hashing (salted per user)
- Stateless JWT login (`HS256`, `iat`/`exp` claims), 1-hour default lifetime
- Bearer-token filter that fails closed: missing, malformed, tampered, or expired tokens → `401`
- Document CRUD, paginated listing (owned **plus shared** documents), paginated JSON envelope
- **Role-based access control** with a single `PermissionService` — never scattered `if` checks
- Owner-only sharing by email, with idempotent role upsert (`VIEWER` ↔ `EDITOR`)
- Bean Validation on all request DTOs with field-level error responses
- Global exception handler with a single, consistent error shape
- N+1 eliminated with `@EntityGraph` (join fetch) on list and detail queries
- Flyway schema migrations, H2-backed integration tests, DevTools hot restart
- Multi-stage `Dockerfile` (non-root runtime) + `docker-compose.yml` (API + PostgreSQL)
- React 19 + TypeScript web client in [`frontend/`](frontend): login/register, protected routes, paginated document list with create/delete

---

## Tech stack & why

| Technology | Version | Why it is here |
|---|---|---|
| **Java** | 21 | Records for immutable DTOs, `Instant`, `List.getFirst()`, virtual-thread ready |
| **Spring Boot** | 4.1.1 | Auto-configuration, production defaults, single runnable jar |
| **Spring Web MVC** | (managed) | Blocking REST API is the simplest thing that works for CRUD |
| **Spring Data JPA / Hibernate** | (managed) | Repository abstraction, derived queries, `@EntityGraph`, dirty checking |
| **Spring Security** | (managed) | Filter chain, BCrypt `PasswordEncoder`, stateless sessions |
| **PostgreSQL** | 16+ | Relational integrity for users ↔ documents, `TIMESTAMPTZ`, future `pgvector` for AI features |
| **Flyway** | (managed) | Versioned, reviewable schema changes instead of `ddl-auto=update` |
| **JJWT** | 0.12.6 | Small, well-maintained JWT library (api / impl / jackson split) |
| **Lombok** | (managed) | Removes entity/getter/setter boilerplate; DTOs stay as plain records |
| **H2** | (test scope) | In-memory database so the whole test suite runs without a network DB |
| **Spring Boot DevTools** | (runtime, optional) | Restart on recompile — the Spring equivalent of `nodemon` |
| **Docker / Compose** | (local) | Reproducible run: API image + PostgreSQL with health-gated startup |
| **Spring Boot Test / MockMvc** | (test scope) | Full-stack HTTP tests without starting a real server |
| **React** | 19 | Component model + hooks for the web client in `frontend/` |
| **TypeScript** | 5.x | Request/response types mirror the backend DTO records |
| **Vite** | 8.x | Instant dev server (`localhost:5173`) and production build |
| **React Router / Axios** | 7.x / 1.x | Protected routes; one axios instance with auth interceptors |

**Deliberately not used:** `ddl-auto=update` (unreviewable schema drift), raw `System.out` logging (SLF4J via Boot), a separate `controller/ service/ repository/` layering (feature packages scale better), password hashing in application code (delegated to `PasswordEncoder`).

---

## Architecture

```
com.sriman.cowrite
├── CowriteApiApplication.java        # entry point, loads .env into system properties
├── auth/                             # feature: registration + login
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── dto/  RegisterRequest, RegisterResponse, LoginRequest, LoginResponse
│   └── exception/ EmailAlreadyRegisteredException, InvalidCredentialsException
├── document/                         # feature: document CRUD
│   ├── Document.java                 # @Entity, @ManyToOne(owner, LAZY)
│   ├── DocumentRepository.java       # @EntityGraph queries, accessible-documents JPQL
│   ├── DocumentService.java          # data access only — no authorization logic
│   ├── DocumentController.java
│   ├── dto/  DocumentRequest, DocumentResponse
│   └── exception/ DocumentNotFoundException
├── permission/                       # feature: sharing + RBAC
│   ├── DocumentRole.java             # VIEWER, EDITOR (owner is never stored here)
│   ├── DocumentPermission.java       # @Entity, unique(document_id, user_id)
│   ├── DocumentPermissionRepository.java
│   ├── PermissionService.java        # isOwner / canView / canEdit + requireView / requireEdit / requireOwner
│   ├── DocumentSharingService.java
│   ├── DocumentSharingController.java
│   ├── dto/  ShareRequest, ShareResponse
│   └── exception/ PermissionDeniedException, CannotShareWithSelfException
├── user/                             # feature: user entity + repository
│   ├── User.java
│   ├── UserRepository.java
│   └── exception/ UserNotFoundException
├── security/                         # cross-cutting: security wiring
│   ├── SecurityConfig.java           # filter chain, PasswordEncoder
│   ├── JwtService.java               # sign / verify / validate
│   └── JwtAuthFilter.java            # Bearer -> SecurityContext
└── common/                           # cross-cutting: shared plumbing
    ├── exception/ ApiError, GlobalExceptionHandler
    └── web/ PageResponse.java
```

**Package-by-feature** keeps each business capability in one place. NestJS developers will recognise the shape: a feature package is a module, `*Controller` is the controller, `*Service` is the provider, and `*Repository` is the data-access layer.

The React client in [`frontend/`](frontend) follows the same idea instead of a flat `components/` dump: `api/` (axios instance + DTO types), `auth/` (token storage, context, hook), `components/` (shared UI), `pages/` (one file per route).

---

## Data model

```
users                               documents
─────────────────────────           ─────────────────────────────
id            BIGSERIAL PK          id          BIGSERIAL PK
email         VARCHAR UNIQUE        title       VARCHAR(200) NOT NULL
password_hash VARCHAR NOT NULL      content     TEXT
created_at    TIMESTAMPTZ           created_at  TIMESTAMPTZ NOT NULL
                                    updated_at  TIMESTAMPTZ NOT NULL
                                    owner_id    BIGINT ─┐
                                                        │ FK -> users(id)

document_permissions
────────────────────────────────────────────────────────────────
id          BIGSERIAL PK
document_id BIGINT FK -> documents(id) ON DELETE CASCADE
user_id     BIGINT FK -> users(id)     ON DELETE CASCADE
role        VARCHAR(20) NOT NULL      -- VIEWER | EDITOR (never ORDINAL)
created_at  TIMESTAMPTZ NOT NULL
UNIQUE (document_id, user_id)
```

- `Document.owner` is `@ManyToOne(fetch = LAZY)` — never loaded unless the query or a DTO mapping asks for it.
- **The owner is not a row in `document_permissions`.** Ownership has exactly one source of truth (`documents.owner_id`); share rows only describe grants on top of it.
- The composite unique constraint means a user holds at most one role per document; re-sharing updates the role instead of duplicating the grant.
- Roles are stored with `@Enumerated(EnumType.STRING)`. `ORDINAL` would silently re-point existing rows if enum constants were ever reordered.
- `created_at` / `updated_at` are managed by `@PrePersist` / `@PreUpdate` — no controller or service can forget them.

Migrations (`src/main/resources/db/migration`):

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__create_documents.sql` | `documents` table |
| V2 | `V2__create_users.sql` | `users` table |
| V3 | `V3__add_owner_to_documents.sql` | `owner_id` FK + index |
| V4 | `V4__create_document_permissions.sql` | shares: table, unique constraint, `user_id` index |

---

## Authentication flow

```
POST /api/auth/register
  client ──{email, password}──► AuthController ──► AuthService
                                    │                 ├─ email normalised (trim + lowercase)
                                    │                 ├─ exists?  -> 409
                                    │                 ├─ BCrypt hash (unique salt per user)
                                    │                 └─ save User(password_hash)
  client ◄── 201 {id, email, createdAt}  (hash never leaves the server)

POST /api/auth/login
  client ──{email, password}──► AuthService
                                    ├─ unknown email        -> 401 "Invalid credentials"
                                    ├─ passwordEncoder.matches(raw, hash) == false -> 401 "Invalid credentials"
                                    └─ JwtService.generateToken(user)
  client ◄── 200 {token}

Protected request
  Authorization: Bearer <jwt>
        │
        ▼
  JwtAuthFilter (Before UsernamePasswordAuthenticationFilter)
        ├─ no header            -> continue as anonymous
        ├─ invalid / expired    -> continue as anonymous
        └─ valid signature      -> load User by id -> SecurityContext (principal = User)
        │
        ▼
  SecurityFilterChain: /api/auth/** permitAll, everything else authenticated
        └─ anonymous  ->  401 (HttpStatusEntryPoint, not the default 403)
```

Token contents: `sub` (user id), `email`, `iat`, `exp`, `HS256` signature. **No secrets in the payload** — a JWT is signed, not encrypted; anyone can base64-decode it.

**Why 401 and not 403:** `HttpStatusEntryPoint(UNAUTHORIZED)` is configured explicitly, because a custom chain with no login mechanism otherwise defaults to `403`.

**Why one message for both failures:** `"Invalid credentials"` for unknown email *and* wrong password prevents user enumeration.

---

## Permissions & roles

Ownership always comes from `documents.owner_id`; every other access right comes from `document_permissions`.

| Role | Where it is stored | Read | Update | Delete | Share |
|---|---|---|---|---|---|
| `OWNER` | `documents.owner_id` | ✅ | ✅ | ✅ | ✅ |
| `EDITOR` | `document_permissions.role` | ✅ | ✅ | ❌ | ❌ |
| `VIEWER` | `document_permissions.role` | ✅ | ❌ | ❌ | ❌ |
| stranger | — | ❌ `403` | ❌ `403` | ❌ `403` | ❌ `403` |

### One place decides — `PermissionService`

```java
permissionService.requireView(id, userId);   // GET    /documents/{id}
permissionService.requireEdit(id, userId);   // PUT    /documents/{id}
permissionService.requireOwner(id, userId);  // DELETE /documents/{id}, POST /documents/{id}/share
```

`requireXxx` either returns normally or throws:

- `DocumentNotFoundException` → `404` (the document does not exist at all)
- `PermissionDeniedException` → `403` (it exists, the caller just may not do this)

The same three methods are what a future WebSocket handler will call before applying an edit. **Never inline permission `if`s in a controller:** the copy you forget to update becomes a security hole the moment a second entry point (WebSocket, background job, GraphQL) appears.

Boolean helpers `isOwner`, `canView`, `canEdit` exist for non-throwing callers (e.g. a UI capability response).

### Two concrete cases

1. **VIEWER tries to update**
   `PUT /api/documents/5` → `requireEdit(5, viewerId)` → owner? no → `document_permissions.role` = `VIEWER` ≠ `EDITOR` → `403 Forbidden`.
   The document is untouched. Hiding the edit button in the UI does not matter — the backend refuses the request.

2. **EDITOR tries to delete or re-share**
   `DELETE /api/documents/5` and `POST /api/documents/5/share` both call `requireOwner` → editor is not the owner → `403 Forbidden`.
   Editing content and administering access are separate powers.

### Sharing

```http
POST /api/documents/5/share
Authorization: Bearer <owner token>
Content-Type: application/json

{ "email": "bob@example.com", "role": "EDITOR" }
```

- only the owner may call it (`403` otherwise)
- the target user must already have an account (`404`)
- sharing with yourself is rejected (`400`)
- calling it again for the same email **updates** the role (`VIEWER` → `EDITOR`) instead of creating a duplicate

`GET /api/documents` returns owned **and** shared documents in one page:

```sql
select d from Document d
where d.owner.id = :userId
   or exists (select 1 from DocumentPermission p
              where p.document.id = d.id and p.user.id = :userId)
```

---

## API reference

Base URL: `http://localhost:8080`

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | public | `201` | duplicate email → `409` |
| `POST` | `/api/auth/login` | public | `200` | returns `{ "token": "..." }` |
| `POST` | `/api/documents` | Bearer | `201` | owner = token subject; `Location` header |
| `GET` | `/api/documents` | Bearer | `200` | paginated; owned **and** shared documents |
| `GET` | `/api/documents/{id}` | Bearer | `200` | owner / editor / viewer; else `403`, missing → `404` |
| `PUT` | `/api/documents/{id}` | Bearer | `200` | owner or editor; viewer → `403` |
| `DELETE` | `/api/documents/{id}` | Bearer | `204` | owner only; editor/viewer → `403` |
| `POST` | `/api/documents/{id}/share` | Bearer | `200` | owner only; `{ email, role }` |

### Register

```http
POST /api/auth/register
Content-Type: application/json

{ "email": "alice@example.com", "password": "password123" }
```

```json
{
  "id": 1,
  "email": "alice@example.com",
  "createdAt": "2026-11-02T06:10:22.451Z"
}
```

Validation: `email` must be a valid address, `password` 8–72 characters (BCrypt only uses 72 bytes).

### Login

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "alice@example.com", "password": "password123" }
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJhbGljZUBleGFtcGxlLmNvbSIsImlhdCI6MTc2MjA2NDIyMiwiZXhwIjoxNzYyMDY3ODIyfQ.3Jk9x0Vn8sQm2pYb7Lc1aW4tR6uEf5hN0oPzXyZaBcE"
}
```

### Create document

```http
POST /api/documents
Authorization: Bearer <token>
Content-Type: application/json

{ "title": "Design notes", "content": "Initial draft of the document." }
```

```json
{
  "id": 1,
  "title": "Design notes",
  "content": "Initial draft of the document.",
  "ownerId": 1,
  "ownerEmail": "alice@example.com",
  "createdAt": "2026-11-02T06:15:42.123456Z",
  "updatedAt": "2026-11-02T06:15:42.123456Z"
}
```

`title`: required, max 200 characters. `content`: optional, may be `null`.

### List documents

```http
GET /api/documents?page=0&size=10&sort=createdAt,desc
Authorization: Bearer <token>
```

### Update / delete

```http
PUT /api/documents/1
Authorization: Bearer <token>
Content-Type: application/json

{ "title": "Design notes v2", "content": "Updated after review." }

DELETE /api/documents/1
Authorization: Bearer <token>
```

### Share a document

```http
POST /api/documents/1/share
Authorization: Bearer <owner token>
Content-Type: application/json

{ "email": "bob@example.com", "role": "VIEWER" }
```

```json
{
  "documentId": 1,
  "userId": 2,
  "email": "bob@example.com",
  "role": "VIEWER"
}
```

`role` must be `VIEWER` or `EDITOR`. Sending the same email again updates the role (idempotent upsert), which is how an owner promotes a viewer to editor.

---

## Error format

Every handled error returns the same shape (`ApiError`):

```json
{
  "timestamp": "2026-11-02T06:20:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "title": "title is required" }
}
```

`fieldErrors` is omitted when empty.

| Status | When |
|---|---|
| `400` | bean validation failure, malformed JSON, sharing a document with yourself |
| `401` | no/invalid/expired token, bad credentials |
| `403` | authenticated but not allowed (e.g. `VIEWER` updating, `EDITOR` deleting, non-owner sharing) |
| `404` | document missing, or the email being shared with has no account |
| `409` | email already registered |

---

## Pagination

`GET /api/documents` never returns a raw Spring `Page` — it is mapped into a stable envelope:

```json
{
  "content": [ { "id": 12, "title": "Design notes", "ownerId": 1, "ownerEmail": "alice@example.com" } ],
  "page": 0,
  "size": 10,
  "totalElements": 12,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

Defaults: `size=10`, sorted by `createdAt` descending. `page`, `size` and `sort` are standard Spring Data parameters.

---

## Performance: the N+1 story

Mapping each document to `DocumentResponse` needs the owner's email. With a lazy association that means one extra `SELECT` per row:

```
100 documents / 10 owners -> 11 queries   (1 documents + 1 per distinct owner)
```

The fix is a join fetch on the only queries that expose the owner:

```java
@EntityGraph(attributePaths = "owner")
@Query(value = "select d from Document d where d.owner.id = :userId " +
               "or exists (select 1 from DocumentPermission p " +
               "           where p.document.id = d.id and p.user.id = :userId)",
       countQuery = "...")
Page<Document> findAllAccessibleByUserId(Long userId, Pageable pageable);
```

```
page of 10 documents -> 2 queries   (1 count + 1 join fetch)
GET /documents/{id}  -> 1 query
```

`DocumentNPlusOneTest` asserts these counts using Hibernate `Statistics`, so a future regression fails the build instead of silently degrading latency.

---

## Testing

```bash
cd backend
./mvnw test          # Windows: mvnw.cmd test
```

```
Tests run: 47, Failures: 0, Errors: 0 — BUILD SUCCESS
```

| Suite | Tests | What it proves |
|---|---|---|
| `AuthApiIntegrationTest` | 12 | BCrypt hash & salt, register validation, 409, JWT login, claims contain no secrets, tampered / malformed / expired token → 401, protected endpoint needs auth |
| `DocumentApiIntegrationTest` | 5 | Full CRUD over HTTP with a real JWT, pagination envelope, a stranger's document → `403`, list is scoped |
| `DocumentSharingIntegrationTest` | 5 | Owner shares; viewer reads but cannot update/delete/re-share (`403`); editor updates but cannot delete/share; role upgrade takes effect; self-share `400`; unknown email `404`; deleting a document removes its shares |
| `PermissionServiceTest` | 11 | `isOwner` / `canView` / `canEdit` for owner, editor, viewer, stranger; `require*` throws `PermissionDeniedException` (`403`) or `DocumentNotFoundException` (`404`) correctly (Mockito) |
| `DocumentServiceTest` | 6 | Owner taken from the principal, accessible-page mapping, exception paths, shares removed before delete (Mockito) |
| `CorsIntegrationTest` | 4 | Preflight from `http://localhost:5173` succeeds without a token (not `401`), allows `POST`/`Authorization`, real requests carry `Access-Control-Allow-Origin`, unknown origins get `403` |
| `DocumentNPlusOneTest` | 3 | Query counts before/after `@EntityGraph` |
| `CowriteApiApplicationTests` | 1 | Application context loads |

Tests run against **H2 in PostgreSQL mode** (`src/test/resources/application-test.properties`), so no database or network is required. DevTools is automatically disabled in tests.

The Docker path was also verified end-to-end: `docker compose up` applies V1–V4 on a fresh PostgreSQL, then a scripted run exercised register → login → create → share (`VIEWER`/`EDITOR`) → viewer `PUT` `403` → editor `PUT` `200` → editor `DELETE` `403` → owner `DELETE` `204`.

The browser flow is covered separately by `frontend/scripts/smoke.mjs` (Playwright, real Chrome): register → reload keeps the session → create → another user's list does not contain the document → delete → logout → protected route redirect.

---

## Getting started

### Prerequisites

- JDK 21
- Node.js 20+ (web client only)
- PostgreSQL 16 (local, Docker, or hosted — Neon/Aiven/Supabase all work)
- No Maven install needed: the Maven wrapper is committed

### 1. Database

Fastest path: skip this step and use [Docker Compose](#running-with-docker) — Flyway creates everything.

Manual setup, apply the migrations in order:

```sql
-- V1
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
-- V2
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
-- V3
ALTER TABLE documents ADD COLUMN owner_id BIGINT REFERENCES users (id);
CREATE INDEX idx_documents_owner_id ON documents (owner_id);
-- V4
CREATE TABLE document_permissions (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_document_permissions_document_user UNIQUE (document_id, user_id)
);
CREATE INDEX idx_document_permissions_user_id ON document_permissions (user_id);
```

Or let Flyway do it: set `spring.flyway.enabled=true` (or `SPRING_FLYWAY_ENABLED=true`) and start the app against an empty database.

> Already have a schema created by hand? Set `spring.flyway.baseline-on-migrate=true` so Flyway baselines instead of failing on the first migration.

### 2. Environment

```bash
cd backend
cp .env.example .env
```

```dotenv
DB_HOST=your-db-host
DB_PORT=5432
DB_NAME=cowrite
DB_USERNAME=your-user
DB_PASSWORD=your-password
# hosted Postgres: require. Local Docker Postgres has no TLS: disable
DB_SSLMODE=require
JWT_SECRET=<at least 32 random characters>
JWT_EXPIRATION=3600000
```

Generate a secret: `openssl rand -base64 48`. The application refuses to start if the secret is shorter than 32 bytes, and there is **no default secret** — a missing `JWT_SECRET` fails fast rather than silently signing tokens with a known key.

> `.env` is git-ignored. `.env.example` contains placeholders only.

### 3. Run

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`.

### 4. Smoke test

```bash
curl -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r .token)

curl -X POST localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Design notes","content":"Initial draft"}'
```

### 5. Manual walkthrough (Postman)

A ready-to-run collection lives in [`docs/postman/Cowrite-RBAC.postman_collection.json`](docs/postman/Cowrite-RBAC.postman_collection.json).

**Import:** Postman → *Import* → select the file → *Run* (Collection Runner). Tokens and the document id are captured automatically, so nothing needs copy/pasting.

**Or from the terminal** (no Postman UI needed):

```bash
npx newman run docs/postman/Cowrite-RBAC.postman_collection.json
```

Expected: `22 requests, 28 assertions, 0 failing`.

Step by step, with three users (`owner@test.com`, `viewer@test.com`, `editor@test.com`, password `password123`):

| # | Request | Body | Expected |
|---|---|---|---|
| 1 | `POST /api/auth/register` ×3 | `{"email":"...","password":"password123"}` | `201` (`409` if re-running) |
| 2 | `POST /api/auth/login` ×3 | same | `200` + `token` |
| 3 | `POST /api/documents` (owner) | `{"title":"My doc","content":"hello"}` | `201`, note `id` |
| 4 | `POST /api/documents/{id}/share` (owner) | `{"email":"viewer@test.com","role":"VIEWER"}` | `200` |
| 5 | `POST /api/documents/{id}/share` (owner) | `{"email":"editor@test.com","role":"EDITOR"}` | `200` |
| 6 | `GET /api/documents/{id}` (viewer) | — | `200` |
| 7 | `PUT /api/documents/{id}` (viewer) | `{"title":"hijack"}` | **`403`** ← the main test |
| 8 | `DELETE /api/documents/{id}` (viewer) | — | **`403`** |
| 9 | `PUT /api/documents/{id}` (editor) | `{"title":"edited"}` | `200` |
| 10 | `DELETE /api/documents/{id}` (editor) | — | **`403`** |
| 11 | `GET /api/documents` (viewer) | — | `200`, `totalElements ≥ 1` (shared doc listed) |
| 12 | `POST /api/documents/{id}/share` (viewer) | `{"email":"x@test.com","role":"VIEWER"}` | **`403`** |
| 13 | `POST /api/documents/{id}/share` (owner) | `{"email":"owner@test.com","role":"VIEWER"}` | **`400`** self-share |
| 14 | `POST /api/documents/{id}/share` (owner) | `{"email":"ghost@test.com","role":"VIEWER"}` | **`404`** unknown user |
| 15 | `POST /api/documents/{id}/share` (owner) | `{"email":"viewer@test.com","role":"EDITOR"}` | `200`, role `EDITOR` |
| 16 | `PUT /api/documents/{id}` (viewer, now upgraded) | `{"title":"promoted viewer edit"}` | `200` (role change took effect) |
| 17 | `DELETE /api/documents/{id}` (owner) | — | `204` |
| 18 | `GET /api/documents/{id}` (viewer) | — | **`404`** (share removed with the document) |

In Postman, set the token per request under **Authorization → Bearer Token** (or send `Authorization: Bearer <token>` manually).

> Use Postman (or `newman`) rather than `curl` inside Windows PowerShell: PowerShell mangles the quotes in inline JSON and the server answers `400 Malformed request body`.

### Hot restart

DevTools is on the classpath. With IntelliJ: enable **Build project automatically** and **Allow auto-make to start even if the application is running**, then `Ctrl+S` restarts the app in ~1–2 seconds. Terminal alternative:

```bash
npx nodemon --watch src/main --ext java,properties,sql --exec "mvnw.cmd -q compile"
```

---

## Running with Docker

One command brings up the API and a PostgreSQL database; Flyway applies all migrations on first start.

```bash
docker compose up --build
```

- API: `http://localhost:8080`
- PostgreSQL: `localhost:55432` (database/user/password `cowrite`, override with `POSTGRES_*` env vars)
- Data survives restarts in the `pgdata` volume — `docker compose down -v` wipes it

```bash
docker compose logs -f app     # follow the API log
docker compose down            # stop, keep data
docker compose down -v         # stop and delete the database volume
```

How it is wired:

| Piece | Detail |
|---|---|
| `backend/Dockerfile` | Multi-stage: Maven build → `eclipse-temurin:21-jre-alpine` runtime, runs as a non-root `app` user |
| `.dockerignore` | Keeps `target/` and `.env` out of the build context |
| Health gating | the app starts only after `pg_isready` marks the database healthy |
| Schema | `SPRING_FLYWAY_ENABLED=true` in Compose, so V1–V4 run on the fresh database |
| TLS | `DB_SSLMODE=disable` for the local container; hosted databases keep `require` |
| Host port | published on `55432`, because `5432` / `5433` are already used by local PostgreSQL installs on this machine. Inside the Compose network the API still talks to `db:5432` |

> Connecting a GUI (DBeaver, DBgate, pgAdmin) to the container? Use **host `localhost`, port `55432`**, database `cowrite`, user `cowrite`, password `cowrite`. Connecting to `localhost:5432` hits whatever local PostgreSQL service owns that port, not this container.
>
> Running the app outside Docker against this database? `DB_HOST=localhost`, `DB_PORT=55432`, `DB_SSLMODE=disable`.

For production, replace the Compose-only `JWT_SECRET` fallback with a real secret (≥ 32 bytes) from your secret manager.

---

## Frontend (React)

The web client lives in [`frontend/`](frontend) — Vite + React 19 + TypeScript — and calls this API over CORS.

### Run it

```bash
# terminal 1 — API
docker compose up -d              # or: cd backend && ./mvnw spring-boot:run

# terminal 2 — web client
cd frontend
npm install
npm run dev                       # http://localhost:5173
```

### What it covers

| Route | Purpose |
|---|---|
| `/login` | `POST /api/auth/login`, stores the JWT |
| `/register` | `POST /api/auth/register`, then logs in automatically |
| `/docs` | paginated list (own + shared), create modal, delete (owner only) |
| `/todo` | Step 2 playground: `useState` / `useEffect` **with cleanup** |

- `ProtectedRoute` redirects to `/login` when no token is stored; `/login` and `/register` redirect away when a session exists.
- One axios instance: the request interceptor adds `Authorization: Bearer …`, the response interceptor clears the token and redirects on `401`.
- The token lives in `localStorage` — fine for a portfolio project, but XSS-readable. The production alternative (in-memory access token + `HttpOnly` refresh cookie) is documented in [`frontend/README.md`](frontend/README.md).
- The token payload is decoded only to show the signed-in email; the server still verifies the signature.

### Verified in a real browser

```bash
cd frontend
npm run smoke        # Playwright + the installed Chrome
```

10 checks: register → session survives a reload → create → **a second user cannot see the document** → delete → logout → `/docs` redirects to `/login`. The backend suite (47 tests) and this browser smoke test are both green.

### CORS

A browser sends a preflight `OPTIONS` request that Postman never sends. It is allowed by:

```properties
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}
```

plus `.cors(Customizer.withDefaults())` on the security chain. Without that, the preflight would be answered with `401` and the browser would report a CORS error even though every curl/Postman call works. `CorsIntegrationTest` pins this behaviour.

---

## Configuration

`backend/src/main/resources/application.properties`

| Property | Value | Notes |
|---|---|---|
| `spring.datasource.*` | from `.env` | `?sslmode=${DB_SSLMODE:require}` — override with `disable` for local Postgres |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate **never** changes the schema |
| `spring.jpa.show-sql` | `true` | dev only |
| `spring.flyway.enabled` | `false` | set `true` to let Flyway manage the schema (Docker Compose does) |
| `jwt.secret` | `${JWT_SECRET}` | required, ≥ 32 bytes |
| `jwt.expiration` | `${JWT_EXPIRATION:3600000}` | milliseconds |
| `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:5173,...}` | comma-separated browser origins; never `*` |

`backend/src/test/resources/application-test.properties` overrides the datasource with H2, sets `ddl-auto=create-drop`, disables Flyway and uses a fixed test-only JWT secret.

---

## Design decisions

| Decision | Rationale |
|---|---|
| Package-by-feature, not package-by-layer | One feature = one folder; smaller diffs, clearer ownership, easier to delete a feature |
| DTO records instead of exposing entities | Request/response contracts are stable and explicit; entities stay persistence-only |
| Owner comes from the JWT principal, never the request body | A client must not be able to create documents on someone else's behalf |
| One `PermissionService` for every entry point | Controller, WebSocket, or job cannot drift apart; a forgotten inline `if` is a security bug |
| Ownership from `documents.owner_id`, other roles from `document_permissions` | One source of truth per fact; no "owner" row that can disagree with `owner_id` |
| `403` for authenticated-but-not-allowed | Distinguishes "you lack rights" from "this does not exist", which the UI needs to react correctly |
| `404` only when the resource truly does not exist | Keeps error semantics honest (`403` for permissions, `404` for absence) |
| `@Enumerated(EnumType.STRING)` for roles | Reordering enum constants can never corrupt stored roles |
| Shutting the document delete path to the owner only | Editing content and administering access are different powers |
| `@EntityGraph` on read queries | N+1 fix with one annotation; no custom JPQL duplication |
| `PageResponse<T>` envelope instead of raw `Page` | Stable JSON contract, no Spring-internals leakage, no Boot warning |
| `ddl-auto=validate` + Flyway | Schema changes are versioned and reviewable |
| BCrypt via `PasswordEncoder` bean | Salt + work factor handled by one battle-tested implementation |
| Same `401` message for all login failures | Prevents user enumeration |
| No default JWT secret | Fails fast instead of shipping a forgeable token key |
| H2 for tests | Fast, hermetic; no shared database, no flaky CI |

---

## Roadmap

- [x] Document sharing and role-based access (`viewer` / `editor` / `owner`)
- [x] Docker Compose for local Postgres + API
- [x] React web client: auth, protected routes, paginated list, create/delete (Milestone 1)
- [ ] Frontend: document editing and sharing UI (API already supports both)
- [ ] Frontend: refresh-token flow instead of a long-lived `localStorage` token
- [ ] Return the caller's effective role in `DocumentResponse` (needs a projection to stay N+1-free)
- [ ] Refresh tokens + logout (token revocation / denylist)
- [ ] Real-time collaboration over WebSocket (CRDT-based editing) — reuses `PermissionService`
- [ ] `documents.owner_id` → `NOT NULL` after backfill
- [ ] Rate limiting (Bucket4j) and login brute-force protection
- [ ] Testcontainers-based integration tests against real PostgreSQL
- [ ] AI assistant: document summarisation + RAG Q&A with `pgvector`
- [ ] OpenAPI/Swagger documentation
- [ ] CI pipeline (test + build + image publish)
