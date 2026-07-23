CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    original_content TEXT NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    document_version BIGINT NOT NULL,
    indexing_status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(2000),
    registered_at TIMESTAMPTZ NOT NULL,
    indexed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_knowledge_documents_status
    ON knowledge_documents (indexing_status, updated_at);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    document_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_messages (
    consumer_name VARCHAR(200) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
