--liquibase formatted sql

--changeset fuzz:4 labels:performance context:dev,prod
--comment: Create performance indexes for bitemporal queries

-- Branch lookup
CREATE INDEX idx_data_entries_branch ON data_entries(branch_id);

-- Primary lookup: branch + data_type + name
CREATE INDEX idx_data_entries_lookup ON data_entries(branch_id, data_type, name);

-- Bitemporal range queries using GIST indexes
CREATE INDEX idx_data_entries_valid_time ON data_entries USING GIST (
    tstzrange(valid_from, valid_to)
);

CREATE INDEX idx_data_entries_transaction_time ON data_entries USING GIST (
    tstzrange(transaction_from, transaction_to)
);

-- Tag-based queries
CREATE INDEX idx_tags_value ON tags(value);
CREATE INDEX idx_tags_data_entry ON tags(data_entry_id);

COMMENT ON INDEX idx_data_entries_lookup IS 'Fast lookup for specific data entries by branch, type, and name';
COMMENT ON INDEX idx_data_entries_valid_time IS 'GIST index for efficient valid time range queries';
COMMENT ON INDEX idx_data_entries_transaction_time IS 'GIST index for efficient transaction time range queries';
COMMENT ON INDEX idx_tags_value IS 'Index for filtering data entries by tag values';

--rollback DROP INDEX idx_data_entries_branch;
--rollback DROP INDEX idx_data_entries_lookup;
--rollback DROP INDEX idx_data_entries_valid_time;
--rollback DROP INDEX idx_data_entries_transaction_time;
--rollback DROP INDEX idx_tags_value;
--rollback DROP INDEX idx_tags_data_entry;
