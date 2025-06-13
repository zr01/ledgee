CREATE TABLE virtual_accounts
(
    id           bigserial primary key,
    public_id    text        not null,
    account_id   text        not null,
    product_code text        not null,
    currency     text        not null default 'AUD',
    metadata     jsonb,
    created_on   timestamptz not null default current_timestamp,
    created_by   text        not null,
    modified_on  timestamptz,
    modified_by  text,
    constraint unique_virt_acct_account_id UNIQUE (account_id, product_code, currency),
    constraint unique_virt_acct_public_id UNIQUE (public_id)
);

CREATE INDEX idx_virt_accts_public_id on virtual_accounts USING btree (public_id);
CREATE INDEX idx_virt_accts_account_id_product on virtual_accounts USING btree (account_id, product_code, currency);

CREATE TABLE ledger
(
    id                    bigserial,
    parent_public_id      text,
    public_id             text        not null,
    account_id            bigint      not null,
    amount                bigint      not null default 0,
    entry_type            smallint    not null default 0,
    is_pending            smallint    not null default 0,
    record_status         smallint    not null default 0,
    external_reference_id text        not null,
    description           text        not null,
    transaction_on        timestamptz not null,
    created_on            timestamptz not null,
    created_by            text        not null,
    PRIMARY KEY (id, is_pending, record_status),
    constraint unique_ledger_public_id_for_account UNIQUE (public_id, is_pending, record_status),
    constraint unique_ledger_external_reference_id UNIQUE (external_reference_id, account_id, entry_type, is_pending,
                                                           record_status)
) PARTITION BY RANGE (is_pending, record_status);

-- record_status cardinal values
-- Staged, WaitingForPair, Balanced, Unbalanced, Excess, Superseded, Error, HotArchive, ColdArchive, ForDeletion

-- pending partitions
CREATE TABLE ledger_staged_pending PARTITION OF ledger FOR VALUES FROM (1, 0) TO (1, 1);
CREATE TABLE ledger_waiting_for_pair_pending PARTITION OF LEDGER FOR VALUES FROM (1, 1) TO (1, 2);
CREATE TABLE ledger_balanced_pending PARTITION OF ledger FOR VALUES FROM (1, 2) TO (1, 3);
CREATE TABLE ledger_unbalanced_pending PARTITION OF ledger FOR VALUES FROM (1, 3) TO (1, 4);
CREATE TABLE ledger_excess_pending PARTITION OF ledger FOR VALUES FROM (1, 4) TO (1, 5);
CREATE TABLE ledger_superseded_pending PARTITION OF ledger FOR VALUES FROM (1, 5) TO (1, 6);
CREATE TABLE ledger_error_pending PARTITION OF ledger FOR VALUES FROM (1, 6) TO (1, 7);
CREATE TABLE ledger_hot_archive_pending PARTITION OF ledger FOR VALUES FROM (1, 7) TO (1, 8);
CREATE TABLE ledger_cold_archive_pending PARTITION OF ledger FOR VALUES FROM (1, 8) TO (1, 9);
CREATE TABLE ledger_for_deletion_pending PARTITION OF ledger FOR VALUES FROM (1, 9) TO (1, 10);

-- money movement partitions
CREATE TABLE ledger_staged PARTITION OF ledger FOR VALUES FROM (0, 0) TO (0, 1);
CREATE TABLE ledger_waiting_for_pair PARTITION OF LEDGER FOR VALUES FROM (0, 1) TO (0, 2);
CREATE TABLE ledger_balanced PARTITION OF ledger FOR VALUES FROM (0, 2) TO (0, 3);
CREATE TABLE ledger_unbalanced PARTITION OF ledger FOR VALUES FROM (0, 3) TO (0, 4);
CREATE TABLE ledger_excess PARTITION OF ledger FOR VALUES FROM (0, 4) TO (0, 5);
CREATE TABLE ledger_superseded PARTITION OF ledger FOR VALUES FROM (0, 5) TO (0, 6);
CREATE TABLE ledger_error PARTITION OF ledger FOR VALUES FROM (0, 6) TO (0, 7);
CREATE TABLE ledger_hot_archive PARTITION OF ledger FOR VALUES FROM (0, 7) TO (0, 8);
CREATE TABLE ledger_cold_archive PARTITION OF ledger FOR VALUES FROM (0, 8) TO (0, 9);
CREATE TABLE ledger_for_deletion PARTITION OF ledger FOR VALUES FROM (0, 9) TO (0, 10);

-- Indexes for the ledgers
CREATE INDEX idx_ldg_acct on ledger USING btree (account_id, entry_type, record_status, external_reference_id);
CREATE INDEX idx_ldg_pubic_id on ledger USING btree (public_id);
CREATE INDEX idx_ldg_parent_public_id on ledger USING btree (parent_public_id);
CREATE INDEX idx_ldg_ext_ref_id on ledger USING btree (external_reference_id);

CREATE TABLE ledger_audit
(
    id                     bigserial   not null,
    ledger_id              bigint      not null,
    previous_record_status smallint,
    new_record_status      smallint,
    change_type            smallint    not null, -- 0: created, 1: updated, 2: status_changed, 3: archived, 4: marked_deletion
    change_reason          text        not null,
    changed_fields         jsonb,                -- Store changed fields and their values
    ip_address             inet,
    user_agent             text,
    created_on             timestamptz not null default now(),
    created_by             text        not null,

    PRIMARY KEY (id, created_on)
) PARTITION BY RANGE (created_on);

-- Create partitions by month (example for 2024)
CREATE TABLE ledger_audit_2025 PARTITION OF ledger_audit
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
-- CREATE TABLE ledger_audit_202402 PARTITION OF ledger_audit
--     FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
-- ... continue for other months

-- Indexes for the audit table
CREATE INDEX idx_audit_ledger_id ON ledger_audit (ledger_id);
CREATE INDEX idx_audit_status_change ON ledger_audit (previous_record_status, new_record_status)
    WHERE change_type = 2;
CREATE INDEX idx_audit_created ON ledger_audit (created_on, created_by);
CREATE INDEX idx_audit_change_type ON ledger_audit (change_type);
