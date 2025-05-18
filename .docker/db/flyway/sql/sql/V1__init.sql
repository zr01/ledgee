CREATE TABLE ledger
(
    id                    bigserial,
    parent_public_id      text,
    public_id             text        not null,
    account_id            text        not null,
    amount                bigint      not null default 0,
    entry_type            smallint    not null default 0,
    is_pending            smallint    not null default 0,
    record_status         smallint    not null default 0,
    external_reference_id text        not null,
    description           text        not null,
    transaction_on        timestamptz not null,
    created_on            timestamptz not null,
    created_by            text        not null,
    PRIMARY KEY (id, is_pending, record_status)
) PARTITION BY RANGE (is_pending, record_status);

-- record_status cardinal values
-- STAGED, UNBALANCED, BALANCED, ERROR, HOT_ARCHIVE, COLD_ARCHIVE, FOR_DELETION

-- pending partitions
CREATE TABLE ledger_staged_pending PARTITION OF ledger FOR VALUES FROM (1, 0) TO (1, 1);
CREATE TABLE ledger_unbalanced_pending PARTITION OF ledger FOR VALUES FROM (1, 1) TO (1, 2);
CREATE TABLE ledger_balanced_pending PARTITION OF ledger FOR VALUES FROM (1, 2) TO (1, 3);
CREATE TABLE ledger_error_pending PARTITION OF ledger FOR VALUES FROM (1, 3) TO (1, 4);
CREATE TABLE ledger_hot_archive_pending PARTITION OF ledger FOR VALUES FROM (1, 4) TO (1, 5);
CREATE TABLE ledger_cold_archive_pending PARTITION OF ledger FOR VALUES FROM (1, 5) TO (1, 6);
CREATE TABLE ledger_for_deletion_pending PARTITION OF ledger FOR VALUES FROM (1, 6) TO (1, 7);

-- money movement partitions
CREATE TABLE ledger_staged PARTITION OF ledger FOR VALUES FROM (0, 0) TO (0, 1);
CREATE TABLE ledger_unbalanced PARTITION OF ledger FOR VALUES FROM (0, 1) TO (0, 2);
CREATE TABLE ledger_balanced PARTITION OF ledger FOR VALUES FROM (0, 2) TO (0, 3);
CREATE TABLE ledger_error PARTITION OF ledger FOR VALUES FROM (0, 3) TO (0, 4);
CREATE TABLE ledger_hot_archive PARTITION OF ledger FOR VALUES FROM (0, 4) TO (0, 5);
CREATE TABLE ledger_cold_archive PARTITION OF ledger FOR VALUES FROM (0, 5) TO (0, 6);
CREATE TABLE ledger_for_deletion PARTITION OF ledger FOR VALUES FROM (0, 6) TO (0, 7);

-- Indexes for the ledgers
CREATE INDEX idx_ldg_acct on ledger USING btree (account_id, entry_type, record_status, external_reference_id);
CREATE INDEX idx_ldg_pubic_id on ledger USING btree (public_id);
CREATE INDEX idx_ldg_parent_public_id on ledger USING btree (parent_public_id);
CREATE INDEX idx_ldg_ext_ref_id on ledger USING btree (external_reference_id);

CREATE TABLE ledger_audit
(
    id                     bigserial not null,
    ledger_id              bigint      not null,
    previous_record_status smallint,
    new_record_status      smallint,
    previous_is_pending    smallint,
    new_is_pending         smallint,
    previous_amount        bigint,
    new_amount             bigint,
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
CREATE TABLE ledger_audit_202505 PARTITION OF ledger_audit
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
-- CREATE TABLE ledger_audit_202402 PARTITION OF ledger_audit
--     FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
-- ... continue for other months

-- Indexes for the audit table
CREATE INDEX idx_audit_ledger_id ON ledger_audit (ledger_id);
CREATE INDEX idx_audit_status_change ON ledger_audit (previous_record_status, new_record_status)
    WHERE change_type = 2;
CREATE INDEX idx_audit_created ON ledger_audit (created_on, created_by);
CREATE INDEX idx_audit_change_type ON ledger_audit (change_type);
