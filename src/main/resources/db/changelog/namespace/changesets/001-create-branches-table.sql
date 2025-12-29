--liquibase formatted sql

--changeset fuzz:1 labels:schema context:dev,prod
--comment: Create branches table (Phase 1: single main branch, Phase 2: full branching)

CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    parent_branch_id UUID REFERENCES branches(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT name_format CHECK (name ~ '^[a-z0-9/_-]+$')
);

COMMENT ON TABLE branches IS 'Git-like branches for organizing data versions';
COMMENT ON COLUMN branches.name IS 'Branch name (e.g., main, develop, feature/xyz)';
COMMENT ON COLUMN branches.parent_branch_id IS 'Parent branch for tracking merge lineage (Phase 2)';

--rollback DROP TABLE branches;
