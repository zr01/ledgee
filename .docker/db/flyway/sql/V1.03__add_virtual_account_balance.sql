CREATE TABLE virtual_account_balance
(
    id                bigserial      NOT NULL,
    account_id        bigint         NOT NULL,
    is_projected      smallint       NOT NULL,
    available_balance numeric(19, 4) NOT NULL DEFAULT 0,
    pending_balance   numeric(19, 4) NOT NULL DEFAULT 0,
    last_updated      timestamptz    NOT NULL DEFAULT current_timestamp,
    version           bigint         NOT NULL DEFAULT 0,
    PRIMARY KEY (id, is_projected),
    CONSTRAINT fk_virtual_account FOREIGN KEY (account_id)
        REFERENCES virtual_accounts (id) ON DELETE CASCADE
) PARTITION BY RANGE (is_projected);

CREATE TABLE virtual_account_balance_actual PARTITION OF virtual_account_balance FOR VALUES FROM (0) TO (1);
CREATE TABLE virtual_account_balance_projected PARTITION OF virtual_account_balance FOR VALUES FROM (1) to (2);

CREATE INDEX idx_virtual_account_balance_account_id ON virtual_account_balance (account_id, is_projected);
