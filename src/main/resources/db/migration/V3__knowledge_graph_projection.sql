CREATE TABLE knowledge_graph_entities (
    id UUID PRIMARY KEY,
    ontology_version VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    canonical_name VARCHAR(300) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL,
    aliases_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_knowledge_graph_entity_identity
        UNIQUE (ontology_version, entity_type, normalized_name)
);

CREATE INDEX idx_knowledge_graph_entities_name
    ON knowledge_graph_entities (normalized_name);

CREATE INDEX idx_knowledge_graph_entities_type_name
    ON knowledge_graph_entities (entity_type, normalized_name);

CREATE TABLE knowledge_graph_entity_evidence (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL REFERENCES knowledge_graph_entities(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id),
    document_version BIGINT NOT NULL,
    chunk_id VARCHAR(500) NOT NULL,
    evidence_quote TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    CONSTRAINT ck_knowledge_graph_entity_evidence_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_knowledge_graph_entity_evidence_entity
    ON knowledge_graph_entity_evidence (entity_id);

CREATE INDEX idx_knowledge_graph_entity_evidence_document
    ON knowledge_graph_entity_evidence (document_id, document_version);

CREATE TABLE knowledge_graph_relations (
    id UUID PRIMARY KEY,
    ontology_version VARCHAR(120) NOT NULL,
    relation_type VARCHAR(120) NOT NULL,
    source_entity_id UUID NOT NULL REFERENCES knowledge_graph_entities(id),
    target_entity_id UUID NOT NULL REFERENCES knowledge_graph_entities(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_knowledge_graph_relation_identity
        UNIQUE (ontology_version, relation_type, source_entity_id, target_entity_id),
    CONSTRAINT ck_knowledge_graph_relation_distinct_endpoints
        CHECK (source_entity_id <> target_entity_id)
);

CREATE INDEX idx_knowledge_graph_relations_source
    ON knowledge_graph_relations (source_entity_id);

CREATE INDEX idx_knowledge_graph_relations_target
    ON knowledge_graph_relations (target_entity_id);

CREATE TABLE knowledge_graph_relation_evidence (
    id UUID PRIMARY KEY,
    relation_id UUID NOT NULL REFERENCES knowledge_graph_relations(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id),
    document_version BIGINT NOT NULL,
    chunk_id VARCHAR(500) NOT NULL,
    evidence_quote TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    CONSTRAINT ck_knowledge_graph_relation_evidence_confidence
        CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_knowledge_graph_relation_evidence_relation
    ON knowledge_graph_relation_evidence (relation_id);

CREATE INDEX idx_knowledge_graph_relation_evidence_document
    ON knowledge_graph_relation_evidence (document_id, document_version);

CREATE TABLE knowledge_graph_projections (
    document_id UUID PRIMARY KEY REFERENCES knowledge_documents(id),
    document_version BIGINT NOT NULL,
    ontology_version VARCHAR(120) NOT NULL,
    entity_count INTEGER NOT NULL,
    relation_count INTEGER NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL
);
