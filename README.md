# Cowrite API

> Backend for a collaborative document editor — Spring Boot 4, PostgreSQL, JWT security, package-by-feature architecture.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-supported-blue)
![Tests](https://img.shields.io/badge/tests-26%20passing-success)

---

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Tech stack & why](#tech-stack--why)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Authentication flow](#authentication-flow)
- [API reference](#api-reference)
- [Error format](#error-format)
- [Pagination](#pagination)
- [Performance: the N+1 story](#performance-the-n1-story)
- [Testing](#testing)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Design decisions](#design-decisions)
- [Roadmap](#roadmap)

---

## Overview

Cowrite is a collaborative document editor. This repository is its **REST API**: users register, log in, and manage their own documents. Every document is scoped to its owner — a user can never read, update, or delete someone else's document.

The codebase is organised **by feature** (not by layer), so a feature is one folder: entity, repository, service, controller, DTOs and exceptions live together. Adding a feature means adding one package, not touching four `controller/`, `service/`, `repository/`, `dto/` folders.

**Current status:** authentication + document CRUD are complete and covered by integration tests. Real-time collaboration (WebSocket/CRDT) is on the roadmap.

---

## Features

- Email/password registration with BCrypt password hashing (salted per user)
- Stateless JWT login (`HS256`, `iat`/`exp` claims), 1-hour default lifetime
- Bearer-token filter that fails closed: missing, malformed, tampered, or expired tokens → `401`
- Document CRUD, paginated listing, paginated JSON envelope
- **Ownership enforcement** at the query level — foreign documents return `404`, never `403`
- Bean Validation on all request DTOs with field-level error responses
- Global exception handler with a single, consistent error shape
- N+1 eliminated with `@EntityGraph` (join fetch) on list and detail queries
- Flyway schema migrations, H2-backed integration tests, DevTools hot restart

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
| **Spring Boot Test / MockMvc** | (test scope) | Full-stack HTTP tests without starting a real server |

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
│   ├── DocumentRepository.java       # @EntityGraph queries, owner-scoped lookups
│   ├── DocumentService.java
│   ├── DocumentController.java
│   ├── dto/  DocumentRequest, DocumentResponse
│   └── exception/ DocumentNotFoundException
├── user/                             # feature: user entity + repository (no public endpoints yet)
│   ├── User.java
│   └── UserRepository.java
├── security/                         # cross-cutting: security wiring
│   ├── SecurityConfig.java           # filter chain, PasswordEncoder
│   ├── JwtService.java               # sign / verify / validate
│   └── JwtAuthFilter.java            # Bearer -> SecurityContext
└── common/                           # cross-cutting: shared plumbing
    ├── exception/ ApiError, GlobalExceptionHandler
    └── web/ PageResponse.java
```

**Package-by-feature** keeps each business capability in one place. NestJS developers will recognise the shape: a feature package is a module, `*Controller` is the controller, `*Service` is the provider, and `*Repository` is the data-access layer.

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
```

- `Document.owner` is `@ManyToOne(fetch = LAZY)` — never loaded unless the query or a DTO mapping asks for it.
- `owner_id` is nullable so pre-auth documents could exist before ownership was introduced; tightening to `NOT NULL` is a roadmap item once all data is backfilled.
- `created_at` / `updated_at` are managed by `@PrePersist` / `@PreUpdate` — no controller or service can forget them.

Migrations (`src/main/resources/db/migration`):

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__create_documents.sql` | `documents` table |
| V2 | `V2__create_users.sql` | `users` table |
| V3 | `V3__add_owner_to_documents.sql` | `owner_id` FK + index |

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

## API reference

Base URL: `http://localhost:8080`

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | public | `201` | duplicate email → `409` |
| `POST` | `/api/auth/login` | public | `200` | returns `{ "token": "..." }` |
| `POST` | `/api/documents` | Bearer | `201` | owner = token subject; `Location` header |
| `GET` | `/api/documents` | Bearer | `200` | paginated, caller's documents only |
| `GET` | `/api/documents/{id}` | Bearer | `200` | not found / not owned → `404` |
| `PUT` | `/api/documents/{id}` | Bearer | `200` | not found / not owned → `404` |
| `DELETE` | `/api/documents/{id}` | Bearer | `204` | not found / not owned → `404` |

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
| `400` | bean validation failure, malformed JSON |
| `401` | no/invalid/expired token, bad credentials |
| `404` | document missing **or not owned by the caller** |
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
@Query(value = "select d from Document d where d.owner.id = :ownerId",
       countQuery = "select count(d) from Document d where d.owner.id = :ownerId")
Page<Document> findAllByOwnerId(Long ownerId, Pageable pageable);
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
Tests run: 26, Failures: 0, Errors: 0 — BUILD SUCCESS
```

| Suite | Tests | What it proves |
|---|---|---|
| `AuthApiIntegrationTest` | 12 | BCrypt hash & salt, register validation, 409, JWT login, claims contain no secrets, tampered / malformed / expired token → 401, protected endpoint needs auth |
| `DocumentApiIntegrationTest` | 5 | Full CRUD over HTTP with a real JWT, pagination envelope, Alice cannot read/update/delete Bob's document (`404`), list is owner-scoped |
| `DocumentServiceTest` | 5 | Owner taken from the principal, owned lookups, exception paths (Mockito) |
| `DocumentNPlusOneTest` | 3 | Query counts before/after `@EntityGraph` |
| `CowriteApiApplicationTests` | 1 | Application context loads |

Tests run against **H2 in PostgreSQL mode** (`src/test/resources/application-test.properties`), so no database or network is required. DevTools is automatically disabled in tests.

---

## Getting started

### Prerequisites

- JDK 21
- PostgreSQL 16 (local, Docker, or hosted — Neon/Aiven/Supabase all work)
- No Maven install needed: the Maven wrapper is committed

### 1. Database

```sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Or apply the Flyway migrations (recommended):

```sql
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
```

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

### Hot restart

DevTools is on the classpath. With IntelliJ: enable **Build project automatically** and **Allow auto-make to start even if the application is running**, then `Ctrl+S` restarts the app in ~1–2 seconds. Terminal alternative:

```bash
npx nodemon --watch src/main --ext java,properties,sql --exec "mvnw.cmd -q compile"
```

---

## Configuration

`backend/src/main/resources/application.properties`

| Property | Value | Notes |
|---|---|---|
| `spring.datasource.*` | from `.env` | `sslmode=require` for hosted Postgres |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate **never** changes the schema |
| `spring.jpa.show-sql` | `true` | dev only |
| `spring.flyway.enabled` | `false` | set `true` to let Flyway manage the schema |
| `jwt.secret` | `${JWT_SECRET}` | required, ≥ 32 bytes |
| `jwt.expiration` | `${JWT_EXPIRATION:3600000}` | milliseconds |

`backend/src/test/resources/application-test.properties` overrides the datasource with H2, sets `ddl-auto=create-drop`, disables Flyway and uses a fixed test-only JWT secret.

---

## Design decisions

| Decision | Rationale |
|---|---|
| Package-by-feature, not package-by-layer | One feature = one folder; smaller diffs, clearer ownership, easier to delete a feature |
| DTO records instead of exposing entities | Request/response contracts are stable and explicit; entities stay persistence-only |
| Owner comes from the JWT principal, never the request body | A client must not be able to create documents on someone else's behalf |
| Ownership embedded in the query (`findByIdAndOwnerId`) | "Forget to check" is impossible — it is one lookup, not a separate `if` |
| Foreign documents return `404`, not `403` | `403` confirms the id exists; `404` leaks nothing |
| `@EntityGraph` on read queries | N+1 fix with one annotation; no custom JPQL duplication |
| `PageResponse<T>` envelope instead of raw `Page` | Stable JSON contract, no Spring-internals leakage, no Boot warning |
| `ddl-auto=validate` + Flyway | Schema changes are versioned and reviewable |
| BCrypt via `PasswordEncoder` bean | Salt + work factor handled by one battle-tested implementation |
| Same `401` message for all login failures | Prevents user enumeration |
| No default JWT secret | Fails fast instead of shipping a forgeable token key |
| H2 for tests | Fast, hermetic; no shared database, no flaky CI |

---

## Roadmap

- [ ] Refresh tokens + logout (token revocation / denylist)
- [ ] Real-time collaboration over WebSocket (CRDT-based editing)
- [ ] Document sharing and role-based access (`viewer` / `editor` / `owner`)
- [ ] `documents.owner_id` → `NOT NULL` after backfill
- [ ] Rate limiting (Bucket4j) and login brute-force protection
- [ ] AI assistant: document summarisation + RAG Q&A with `pgvector`
- [ ] OpenAPI/Swagger documentation
- [ ] Docker Compose for local Postgres + CI pipeline (test + build)
