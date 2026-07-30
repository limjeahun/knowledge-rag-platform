CREATE TABLE knowledge_ontology_versions (
    version_iri VARCHAR(500) PRIMARY KEY,
    ontology_iri VARCHAR(500) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    ontology_format VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_knowledge_ontology_format
        CHECK (ontology_format IN ('JSON', 'OWL')),
    CONSTRAINT ck_knowledge_ontology_status
        CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_knowledge_ontology_checksum
        CHECK (char_length(checksum) = 64)
);

CREATE TABLE knowledge_graph_projection_runs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id),
    document_version BIGINT NOT NULL,
    ontology_version_iri VARCHAR(500) NOT NULL REFERENCES knowledge_ontology_versions(version_iri),
    backend VARCHAR(30) NOT NULL,
    graph_names_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(30) NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT ck_knowledge_graph_projection_backend
        CHECK (backend IN ('POSTGRES', 'FUSEKI')),
    CONSTRAINT ck_knowledge_graph_projection_run_status
        CHECK (status IN ('ACTIVE', 'RETIRED', 'FAILED'))
);

CREATE UNIQUE INDEX uk_knowledge_graph_active_projection
    ON knowledge_graph_projection_runs (document_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_knowledge_graph_projection_document_version
    ON knowledge_graph_projection_runs (document_id, document_version);

CREATE INDEX idx_knowledge_graph_projection_ontology
    ON knowledge_graph_projection_runs (ontology_version_iri, status);
