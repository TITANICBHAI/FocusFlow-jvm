---
name: react-vite
description: Required guidelines for web apps. Read before creating one or making any change to an existing web app — features, fixes, and edits included.
---

Use the current runtime tools and callbacks. Do not call legacy callback names from older skill ports.

Always follow these guidelines when creating or modifying a React + Vite web application:

## First builds

The complete first-build workflow — the design-subagent mandate, app classification, API-surface planning, and build ordering — is in `.local/skills/react-vite/references/first-build.md`. It is loaded automatically when the artifact is created; if you are building a react-vite app from scratch and it is not already in context, read it before writing any code.

## Architecture

- Follow modern web application patterns and best-practices.
- If the app is complex and requires functionality that can't be done in a single request, it is okay to stub out the backend and implement the frontend first.

## Backend

Use the `pnpm-workspace` skill as the source of truth for shared monorepo rules. When you touch backend code, follow the `pnpm-workspace` skill's references:

- `references/openapi.md` for contract-first OpenAPI + codegen
- `references/server.md` for `artifacts/api-server/src/routes/` conventions
- `references/db.md` for `lib/db/src/schema/` and Drizzle guidance

After each OpenAPI spec change, re-run codegen before using the updated types.

## Frontend

- Shared frontend conventions live in `.local/skills/react-vite/references/frontend-general-rules.md`. Pass that file to design subagents via `relevantFiles`.
- For substantial new frontend surfaces, delegate to the design subagent (read the `design` skill). For first builds the design subagent is mandatory — see `references/first-build.md`.

## Services and routing

- Follow the service access and routing rules from the `pnpm-workspace` skill.
- **WebSocket proxy path**: If the app uses WebSockets, the WS path (e.g. `/ws`) must be listed in `artifact.toml`'s `paths` array alongside the REST API path. The proxy only forwards explicitly listed paths -- unlisted WS paths are silently dropped and the server never sees the connection.

## SEO

There is a full SEO implementation guide in `.local/skills/react-vite/references/seo.md`. Read it when building or optimizing pages for search engine visibility. At minimum, ensure every page has a unique title tag, meta description, and Open Graph tags.
