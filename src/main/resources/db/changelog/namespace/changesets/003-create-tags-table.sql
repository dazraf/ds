--liquibase formatted sql

--changeset fuzz:3 labels:schema context:dev,prod
--comment: Create tags table (simple string values, versioned with data)

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_entry_id UUID NOT NULL REFERENCES data_entries(id) ON DELETE CASCADE,
    value VARCHAR(255) NOT NULL,

    UNIQUE(data_entry_id, value)
);

COMMENT ON TABLE tags IS 'Simple string tags associated with data entries';
COMMENT ON COLUMN tags.value IS 'Tag value (not key-value pairs, just strings)';
COMMENT ON CONSTRAINT tags_data_entry_id_value_key ON tags IS 'Prevent duplicate tags on same entry';

--rollback DROP TABLE tags;
