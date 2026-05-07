CREATE TABLE users (
    intuit_account_id UUID PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    role              VARCHAR(16)  NOT NULL
                      CHECK (role IN ('CONSUMER', 'MERCHANT', 'CONTRACTOR')),
    home_region       VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));
