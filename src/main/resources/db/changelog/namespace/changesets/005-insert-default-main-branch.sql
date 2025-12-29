--liquibase formatted sql

--changeset fuzz:5 labels:data context:dev,prod
--comment: Insert default 'main' branch for new namespaces

INSERT INTO branches (name, created_by, parent_branch_id)
VALUES ('main', 'system', NULL);

COMMENT ON TABLE branches IS 'Default main branch created for all new namespaces';

--rollback DELETE FROM branches WHERE name = 'main' AND created_by = 'system';
