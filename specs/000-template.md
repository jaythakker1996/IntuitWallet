# NNN — <feature name>

- **Status:** Draft | Approved | Implemented
- **Author:** <name>
- **Last updated:** <YYYY-MM-DD>
- **Implements:** <link to PR / commits when Implemented>
- **Related ADRs:** <list, or "none">

## Problem

What problem are we solving? Who hits it? What does the world look like today vs. after this ships?

## Goals / Non-goals

- **Goals**
  - …
- **Non-goals**
  - …

## API

| Method | Path | Description |
| --- | --- | --- |
| POST | /api/... | … |

### Request

```json
{
  "...": "..."
}
```

Validation: …

### Response

```json
{
  "...": "..."
}
```

### Error cases

| Status | When |
| --- | --- |
| 400 | … |
| 404 | … |

## Data model

Tables added/changed (Flyway migration `V<n>__<name>.sql`):

```sql
CREATE TABLE example (...);
```

Indexes / foreign keys / constraints:

- …

## Workflow

- **Workflow name:** `ExampleWorkflow`
- **Task queue:** `wallet-service`
- **Method signature:** `ExampleResult run(ExampleInput input)`
- **Activities:**
  - `ActivityA.do(...)` — start-to-close timeout: …, retries: …
  - `ActivityB.do(...)` — …
- **Signals / queries:** N/A or describe.
- **Idempotency:** how a retried workflow / activity stays correct.

## Acceptance criteria

- [ ] Endpoint accepts a valid request and returns the expected response shape.
- [ ] Workflow persists the expected rows in the expected tables.
- [ ] Errors map to the documented HTTP status codes.
- [ ] …

## Test plan

- Unit tests for `core` services (rules + edge cases).
- `TestWorkflowEnvironment` test for the workflow and its activities.
- Manual smoke against `docker compose up -d` (curl example + expected response).

## Open questions

- …
