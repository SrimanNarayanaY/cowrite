ALTER TABLE documents
    ADD COLUMN owner_id BIGINT REFERENCES users (id);

CREATE INDEX idx_documents_owner_id ON documents (owner_id);
