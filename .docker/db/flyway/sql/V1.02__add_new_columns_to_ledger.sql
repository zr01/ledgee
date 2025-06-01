-- Add new columns
ALTER TABLE ledger
    ADD COLUMN entry_reference_id TEXT,
    ADD COLUMN reconciled_on      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reconciled_by      TEXT;

-- Create indexes for the new columns
CREATE INDEX idx_ledger_entry_reference_id ON ledger USING btree (entry_reference_id);
CREATE INDEX idx_ledger_reconciled_on ON ledger USING btree (reconciled_on);
CREATE INDEX idx_ledger_reconciled_by ON ledger USING btree (reconciled_by);
