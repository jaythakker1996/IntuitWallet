# Specs

Every behavior change in IntuitWalletService starts with a spec here.

## Naming

Files are numbered and kebab-cased: `NNN-feature-name.md`. Numbers are monotonically increasing across the project; pick the next free one.

`000-template.md` is the template. Copy it for each new spec.

## Lifecycle

- **Draft** — author is iterating; not safe to implement against.
- **Approved** — reviewers signed off; implementation can begin.
- **Implemented** — code is merged. Update the status and link the merge commit / PR.

Lifecycle status lives at the top of each spec.

## What goes in a spec

A spec is a contract. After reading it, an implementer should know:

- What problem it solves and why now.
- The HTTP API surface (paths, methods, request/response shape, validation rules).
- The data model (tables, fields, indexes, foreign keys).
- The Temporal workflow (signature, activities, retry/timeout choices, signals/queries if any).
- What "done" looks like (acceptance criteria, test plan).

If a section doesn't apply (e.g. no new tables), say "N/A" — don't delete the heading.

## Relationship to ADRs

If a spec forces a cross-cutting architectural choice (a new infrastructure piece, a new framework, a new pattern that other features will follow), spin off an ADR in `../adrs/` and link it from the spec.
