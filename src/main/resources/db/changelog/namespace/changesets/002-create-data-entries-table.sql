--liquibase formatted sql

--changeset fuzz:2 labels:schema context:dev,prod
--comment: Create data_entries table with bitemporal support

CREATE TABLE data_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES branches(id),
    data_type VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,

    -- Bitemporal columns
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL DEFAULT 'infinity'::TIMESTAMPTZ,
    transaction_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transaction_to TIMESTAMPTZ NOT NULL DEFAULT 'infinity'::TIMESTAMPTZ,

    -- Data payload
    data BYTEA NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,

    -- Metadata
    created_by VARCHAR(255) NOT NULL,

    -- Constraints
    CONSTRAINT valid_time_range CHECK (valid_from < valid_to),
    CONSTRAINT transaction_time_range CHECK (transaction_from < transaction_to)
);

COMMENT ON TABLE data_entries IS 'Bitemporal data storage with branch awareness';
COMMENT ON COLUMN data_entries.branch_id IS 'Branch this entry belongs to (branching-ready for Phase 2)';
COMMENT ON COLUMN data_entries.data_type IS 'Logical data type (e.g., documents, images, contracts)';
COMMENT ON COLUMN data_entries.name IS 'Unique name within data_type and branch';
COMMENT ON COLUMN data_entries.valid_from IS 'Valid time: when this data became true in the real world';
COMMENT ON COLUMN data_entries.valid_to IS 'Valid time: when this data stopped being true (infinity if current)';
COMMENT ON COLUMN data_entries.transaction_from IS 'Transaction time: when this data was recorded in the database';
COMMENT ON COLUMN data_entries.transaction_to IS 'Transaction time: when this data was superseded (infinity if current)';
COMMENT ON COLUMN data_entries.data IS 'Opaque binary data (BYTEA)';
COMMENT ON COLUMN data_entries.media_type IS 'MIME type / content type of the data';
COMMENT ON COLUMN data_entries.size_bytes IS 'Size of data in bytes';

--rollback DROP TABLE data_entries;
