--liquibase formatted sql

--changeset fuzz:1 labels:schema context:dev,prod
--comment: Create namespaces registry table for tracking namespace metadata

CREATE TABLE namespaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    database_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    CONSTRAINT name_format CHECK (name ~ '^[a-z0-9-]+$'),
    CONSTRAINT status_values CHECK (status IN ('active', 'suspended', 'deleted'))
);

CREATE INDEX idx_namespaces_name ON namespaces(name);
CREATE INDEX idx_namespaces_status ON namespaces(status);

COMMENT ON TABLE namespaces IS 'Registry of all namespaces and their corresponding databases';
COMMENT ON COLUMN namespaces.name IS 'User-facing namespace name';
COMMENT ON COLUMN namespaces.database_name IS 'Actual PostgreSQL database name (e.g., ds_ns_my-namespace)';
COMMENT ON COLUMN namespaces.status IS 'Namespace status: active, suspended, or deleted';

--rollback DROP TABLE namespaces;
