# Architecture Decision Records (ADRs)

ADRs capture cross-cutting decisions that aren't tied to a single feature spec. Examples: choosing a framework, adopting a pattern across packages, picking an infrastructure component, accepting a constraint that affects future work.

If the decision is feature-specific, it lives in `../specs/` instead.

## Naming

Files are numbered and kebab-cased: `NNN-decision-name.md`. Numbers are monotonically increasing across the project.

`000-template.md` is the template.

## Status values

- **Proposed** — under discussion.
- **Accepted** — in force; implementations should follow it.
- **Superseded by NNN** — replaced; link the new ADR.

Once an ADR is Accepted, the only edits should be (a) marking it Superseded, or (b) typo/clarity fixes that don't change the decision.
