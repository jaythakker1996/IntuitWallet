-- spec/011: QR codes table per ADR 006.
-- One QR per wallet (UNIQUE constraint on wallet_id). No FK to wallets per
-- ADR 004 §2 (cross-region active-active portability). expires_at is nullable
-- and always NULL in the POC ("lasts forever"); status is always 'ACTIVE'.

CREATE TABLE qr_codes (
    qr_code_id  UUID         PRIMARY KEY,
    wallet_id   UUID         NOT NULL UNIQUE,
    payload     TEXT         NOT NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'STATIC'
                CHECK (type IN ('STATIC', 'DYNAMIC')),
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'REVOKED')),
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
