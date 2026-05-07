-- spec/007: SYSTEM wallets may run negative running_available (e.g. external_deposits
-- accumulates DEBITs over time as money flows in from outside Intuit). Application
-- code (LedgerCoreService) enforces the non-negative invariant for USER wallets
-- before each debit. The DB-level CHECK was redundant defense-in-depth and blocks
-- the SYSTEM-side semantics; ADR 004 §4 amendment in the same PR captures the move.

ALTER TABLE ledger_entries
    DROP CONSTRAINT ledger_entries_running_available_check;
