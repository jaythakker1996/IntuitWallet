-- spec/005: Ledger schema bootstrap (ADR 003 + ADR 004).
-- Creates wallets, transactions, ledger_entries and seeds the four
-- SYSTEM wallets. No foreign keys per ADR 004 §2 (cross-region active-active
-- portability). Partitioning is deferred per ADR 004 §6 and reintroduced
-- via copy-rebuild before production rollout.

-- =============================================================
-- wallets (per ADR 003 §2)
-- =============================================================
CREATE TABLE wallets (
    wallet_id         UUID         PRIMARY KEY,
    intuit_account_id UUID         NOT NULL UNIQUE,
    type              VARCHAR(16)  NOT NULL
                      CHECK (type IN ('USER', 'SYSTEM')),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =============================================================
-- transactions (per ADR 004 §3)
-- =============================================================
CREATE TABLE transactions (
    tx_id                UUID          NOT NULL,
    type                 VARCHAR(16)   NOT NULL
                         CHECK (type IN ('SEND', 'RECEIVE', 'QR_PAY',
                                         'FUND', 'WITHDRAW', 'BATCH_DEBIT',
                                         'SETTLEMENT', 'REVERSAL')),
    from_type            VARCHAR(16)   NOT NULL
                         CHECK (from_type IN ('WALLET', 'EXTERNAL')),
    from_party           TEXT          NOT NULL,
    to_type              VARCHAR(16)   NOT NULL
                         CHECK (to_type IN ('WALLET', 'EXTERNAL')),
    to_party             TEXT          NOT NULL,
    stablecoin           VARCHAR(16)   NOT NULL,
    amount               NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    fee                  NUMERIC(28,8) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    status               VARCHAR(16)   NOT NULL
                         CHECK (status IN ('PENDING', 'COMPLETED',
                                           'FAILED', 'REVERSED')),
    blockchain_tx_hash   TEXT,
    compliance_check_id  UUID,
    batch_id             UUID,
    idempotency_key      TEXT          NOT NULL,
    request_hash         TEXT          NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tx_id)
);

CREATE UNIQUE INDEX idx_tx_idempotency
    ON transactions (from_party, idempotency_key);
CREATE INDEX idx_tx_from_created
    ON transactions (from_party, created_at DESC);
CREATE INDEX idx_tx_to_created
    ON transactions (to_party, created_at DESC);
CREATE INDEX idx_tx_pending
    ON transactions (status) WHERE status = 'PENDING';

-- =============================================================
-- ledger_entries (per ADR 004 §4)
-- =============================================================
CREATE TABLE ledger_entries (
    entry_id           UUID          NOT NULL,
    tx_id              UUID          NOT NULL,
    wallet_id          UUID          NOT NULL,
    stablecoin         VARCHAR(16)   NOT NULL,
    entry_type         VARCHAR(16)   NOT NULL
                       CHECK (entry_type IN ('DEBIT', 'CREDIT',
                                             'SETTLEMENT', 'REVERSAL')),
    amount             NUMERIC(28,8) NOT NULL CHECK (amount > 0),
    running_available  NUMERIC(28,8) NOT NULL CHECK (running_available >= 0),
    running_pending    NUMERIC(28,8) NOT NULL CHECK (running_pending >= 0),
    entry_sequence     BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entry_id)
);

CREATE UNIQUE INDEX idx_ledger_seq
    ON ledger_entries (wallet_id, stablecoin, entry_sequence);
CREATE INDEX idx_ledger_latest
    ON ledger_entries (wallet_id, stablecoin, entry_sequence DESC);
CREATE INDEX idx_ledger_tx
    ON ledger_entries (tx_id);

-- =============================================================
-- SYSTEM wallet seed (per ADR 004 §8)
-- Reserved synthetic intuit_account_id range: ...0ed - ...0f0
-- Logical name -> wallet_id:
--   external_deposits     -> ...ed01
--   external_withdrawals  -> ...ed02
--   fee_revenue           -> ...ed03
--   treasury              -> ...ed04
-- =============================================================
INSERT INTO wallets (wallet_id, intuit_account_id, type, status) VALUES
    ('00000000-0000-0000-0000-00000000ed01',
     '00000000-0000-0000-0000-0000000000ed', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed02',
     '00000000-0000-0000-0000-0000000000ee', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed03',
     '00000000-0000-0000-0000-0000000000ef', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-0000-0000-00000000ed04',
     '00000000-0000-0000-0000-0000000000f0', 'SYSTEM', 'ACTIVE');
