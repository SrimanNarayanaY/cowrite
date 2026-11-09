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
  - [How the UI works, end to end](#how-the-ui-works-end-to-end)
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

### How the UI works, end to end

This section follows one user journey — open the app, sign in, see the list, create a document — and names the file that implements each step. Read it top to bottom and the frontend stops feeling like magic. No prior React knowledge is assumed; the few concepts that matter are explained where they appear.

#### The mental model: two kinds of state

Almost every confusing React bug comes from mixing these up:

| Kind of state | Where it lives | Examples |
|---|---|---|
| **Session state** | `AuthProvider`, readable anywhere with `useAuth()` | are we signed in, which email is signed in |
| **Screen state** | one page component, via `useState` | current page number, rows on screen, is the create dialog open |

Documents themselves are **not** frontend state. The API is the source of truth. After a write the page does not patch its own copy of the list — it asks the server for the list again. That costs one extra request and removes an entire class of bugs where the UI and the database quietly disagree.

#### Layer map

```
main.tsx                  mounts <App /> into #root
└── App.tsx               the route table, nothing else
    └── AuthProvider      session state: token, email, login / register / logout
        └── BrowserRouter History API — no full page reloads
            └── Routes
                ├── /login, /register   → PublicOnlyRoute
                ├── /docs, /todo        → ProtectedRoute → Layout → <Outlet />
                └── /                   → <Navigate to="/docs" />
```

Every path below is relative to [`frontend/src`](frontend/src).

#### Step 1 — Startup: restore the session before the first paint

`AuthProvider` initialises state with a **lazy initialiser**:

```tsx
const [session, setSession] = useState<Session | null>(() => readSession())
```

`readSession()` in `auth/token.ts` runs exactly once, on the first render. It reads `cowrite.token` from `localStorage`, decodes the JWT payload, and compares `exp` with the current time:

| Stored value | Result |
|---|---|
| nothing | `null` — signed out |
| malformed or expired token | token is deleted, `null` — signed out |
| valid token | `{ token, email }` — signed in |

Doing this **before** the first render is what makes a refresh feel invisible: a signed-in user reloads `/docs` and stays on `/docs`, while an expired token redirects to `/login` instead of flashing a dashboard that would immediately answer `401`.

The payload is decoded for **display only** — the header shows the email. The backend still verifies the signature on every request: a client can read a JWT, but it cannot forge one.

#### Step 2 — Route guards decide what may render

`components/ProtectedRoute.tsx` exports two guards:

| Guard | Protects | Session present | Session absent |
|---|---|---|---|
| `ProtectedRoute` | `/docs`, `/todo` (the whole `Layout`) | renders `children` | `<Navigate to="/login" replace state={{ from }} />` |
| `PublicOnlyRoute` | `/login`, `/register` | `<Navigate to="/docs" replace />` | renders `children` |

The `from` value is why the round trip is pleasant. A signed-out user who opens `/docs` is sent to `/login` **with the original path remembered**; after a successful login they land on `/docs`, not on a generic home page.

> `ProtectedRoute` is a **UX guard, not a security boundary**. Anyone can flip React state in devtools. The real gate is the backend: `401` without a valid JWT, `403` without permission. Hiding a button is presentation, never authorization.

#### Step 3 — Signing in

`pages/LoginPage.tsx` owns the form state (`email`, `password`, `error`, `submitting`) and calls `login()` from the auth context:

```
LoginPage.handleSubmit
  → AuthProvider.login              (auth/AuthProvider.tsx)
      → POST /api/auth/login        { "email": "…", "password": "…" }
      ← 200 OK                      { "token": "<jwt>" }
      → setToken(token)             localStorage["cowrite.token"]
      → setSession({ token, email })
  → navigate(from ?? '/docs', { replace: true })
```

Three details worth knowing:

1. **`setSession` is the moment the app becomes authenticated.** `isAuthenticated` flips to `true`, every consumer re-renders, and `PublicOnlyRoute` would leave `/login` even if `navigate()` were never called.
2. **`replace: true`** replaces the `/login` entry in browser history, so the Back button does not return to the form after signing in.
3. **A failed login must not trigger the global `401` handler.** The response interceptor in `api/client.ts` redirects only when a token **already existed**, so a typo produces an inline "Invalid credentials" message instead of a page reload.

`/register` is the same flow with one extra step: `POST /api/auth/register` returns `201 Created` without a token, so `AuthProvider.register` calls `login()` immediately afterwards.

#### Step 4 — The authenticated shell

After `navigate('/docs')`, React Router renders the parent route declared in `App.tsx`:

```tsx
<Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
  <Route path="/docs" element={<DocumentsPage />} />
  <Route path="/todo" element={<TodoPage />} />
</Route>
```

`Layout` draws the top bar (brand, navigation, signed-in email, log out) and then `<Outlet />` — the placeholder where the matched child page is injected. This is why the top bar does not disappear or re-mount when moving from `/docs` to `/todo`: only the outlet's content changes.

Navigation uses `<NavLink>` rather than `<a href>`, so the History API updates the URL and React swaps the page **without a full browser reload**. The `isActive` callback adds the `active` class to the current link.

#### Step 5 — Loading the document list

`DocumentsPage` fetches on mount and whenever the page number or an explicit reload changes:

```tsx
useEffect(() => {
  const controller = new AbortController()
  let active = true

  api.get('/documents', { params: { page, size: 5 }, signal: controller.signal })
    .then((response) => { if (active) setData(response.data) })
    .catch((err) => {
      if (!active || axios.isCancel(err)) return
      setError(errorMessage(err))
    })
    .finally(() => { if (active) setLoading(false) })

  return () => { active = false; controller.abort() }
}, [page, reloadKey])
```

What the dependencies mean: `page` changes when the user paginates, and `reloadKey` is a counter that `reload()` increments. There is no `setState` synchronously in the effect body — `loading` and `error` are set by the handler that **starts** the action (paginate, retry, delete, create), while the effect only performs I/O. That is what keeps the request loop terminating instead of re-triggering itself.

The component never mentions a token. `api/client.ts` attaches it in a request interceptor:

```
request interceptor → Authorization: Bearer <token from localStorage>
```

So no page can forget the header, and exactly one file knows how authentication travels over HTTP.

The response is the envelope documented in [Pagination](#pagination):

```json
{
  "content": [
    {
      "id": 12,
      "title": "Design notes",
      "content": "…",
      "ownerId": 1,
      "ownerEmail": "alice@example.com",
      "createdAt": "2026-01-14T09:12:00Z",
      "updatedAt": "2026-01-14T09:30:00Z"
    }
  ],
  "page": 0,
  "size": 5,
  "totalElements": 12,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

`AbortController` plus the `active` flag solve the "slow response overwrites a newer one" problem: clicking *Next* twice quickly aborts the first request, and a late callback from an unmounted component is ignored.

#### Step 6 — How a row decides what to show

Each item in `content` becomes a card. The only branch that matters is ownership:

```tsx
const mine = document.ownerEmail !== null && document.ownerEmail === email
```

| Condition | Badge | Delete button |
|---|---|---|
| `mine === true` | `Yours` | visible |
| shared with me | `Shared by alice@example.com` | hidden |

A document with no content shows "No content yet." instead of the preview; the badge and delete rules above are unchanged.

Hiding the delete button is presentation. A shared user who calls `DELETE /api/documents/{id}` directly still receives `403` from the backend.

Empty and error states are explicit rather than accidental: "No documents yet" with a create button when the list is empty and the request succeeded; a retry bar when the request failed; and "Loading documents…" only while the **first** page is in flight, so pagination does not flicker the whole list.

#### Step 7 — Creating a document

The same dialog is opened from two places in `DocumentsPage`:

- the **New document** button in the page header;
- the **Create a document** button inside the empty state — a brand-new account would otherwise stare at a blank page.

Both call `setCreateOpen(true)`. At the bottom of the component:

```tsx
{createOpen && <CreateDocumentDialog onClose={() => setCreateOpen(false)} onCreated={…} />}
```

The dialog (`components/CreateDocumentDialog.tsx`) owns its own `title` / `content` / `saving` / `error` state and submits:

```
POST /api/documents
{ "title": "Design notes", "content": "…" }     // content is null when left empty
```

The body contains only `title` and `content`. **The owner is neither sent nor selectable** — the backend reads the authenticated principal from the JWT and sets `owner_id` itself. This is the frontend half of the ownership rule the backend enforces.

On success the dialog calls `onCreated()` and closes. `onCreated` does not splice the new document into the array by hand; it triggers a refetch:

```tsx
onCreated={() => {
  if (page === 0) reload()      // reloadKey++  → the effect runs again
  else goToPage(0)              // jump to page 0 → the effect runs again
}}
```

Jumping to page 0 is deliberate: the list is sorted newest-first, so a new document is guaranteed to be visible there. Refetching instead of patching local state means the row on screen is exactly what the server stored, including server-generated fields such as `id`, `createdAt` and `updatedAt`.

The dialog also demonstrates a `useEffect` **with cleanup**: an Escape-key listener is added on mount and removed on unmount, so opening and closing the modal repeatedly does not leak listeners.

#### Step 8 — Pagination and deletion

Pagination is server-side. `goToPage(next)` sets `loading`, clears the previous error, and updates `page`; the effect then requests exactly that page. The buttons are disabled from the envelope itself — `data.first` disables *Previous*, `data.last` disables *Next* — so the UI never guesses the bounds.

Deletion follows the same "change, then refetch" rule, with one edge case:

```tsx
if (data && data.content.length === 1 && !data.first) {
  goToPage(Math.max(0, data.page - 1))   // last row on a page → step back
} else {
  reload()
}
```

Without that branch, deleting the only row of page 3 would leave the user on an empty page after `totalPages` had already shrunk to 2.

#### Step 9 — Logging out, and the global `401` path

The top bar's **Log out** calls `logout()` (`clearToken()` + `setSession(null)`) and navigates to `/login`. Even if the navigation were skipped, `ProtectedRoute` would redirect on the next render because the session is gone.

There is a second, non-interactive exit. If any request returns `401` **while a token was stored**, the response interceptor deletes the token and redirects to `/login`. That is what expiry looks like in practice: the user clicked a link, the server rejected the stale token, and the app returned to the sign-in screen instead of leaving an error box on a page that can never load.

#### The whole journey on one page

```
Browser loads /
  → readSession()                       valid token? ── no ──→ /login
  → /                                   (redirect)
  → /docs   ProtectedRoute ── yes ──→   Layout + DocumentsPage
                                          → GET /api/documents?page=0&size=5
                                            (Authorization: Bearer …)
                                          ← 200 PageResponse
                                          → cards + pagination
  → "New document" → dialog → POST /api/documents → reload → GET again
  → Log out (or a 401) → token cleared → /login
```

#### Design rules this section is meant to leave you with

1. **The server owns the data.** A write ends with a refetch, never with a hand-edited local copy.
2. **One HTTP client.** Auth headers and `401` handling live in `api/client.ts`, not in pages.
3. **Session state is global, screen state is local.** Only what the whole app needs (who is signed in) goes into context.
4. **The guard is UX; the API is security.** Hiding a button is not authorization.
5. **Effects start I/O; handlers set flags.** That is what keeps `useEffect` from looping.

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
