CREATE TABLE document_permissions (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_document_permissions_document_user UNIQUE (document_id, user_id)
);

-- "documents shared with me" query
CREATE INDEX idx_document_permissions_user_id ON document_permissions (user_id);
