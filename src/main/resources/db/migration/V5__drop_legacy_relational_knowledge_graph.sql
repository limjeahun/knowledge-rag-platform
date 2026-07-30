DROP TABLE IF EXISTS knowledge_graph_relation_evidence;
DROP TABLE IF EXISTS knowledge_graph_entity_evidence;
DROP TABLE IF EXISTS knowledge_graph_relations;
DROP TABLE IF EXISTS knowledge_graph_projections;
DROP TABLE IF EXISTS knowledge_graph_entities;

ALTER TABLE knowledge_ontology_versions
    DROP CONSTRAINT ck_knowledge_ontology_format;
ALTER TABLE knowledge_ontology_versions
    ADD CONSTRAINT ck_knowledge_ontology_format
        CHECK (ontology_format = 'OWL') NOT VALID;

ALTER TABLE knowledge_graph_projection_runs
    DROP CONSTRAINT ck_knowledge_graph_projection_backend;
ALTER TABLE knowledge_graph_projection_runs
    ADD CONSTRAINT ck_knowledge_graph_projection_backend
        CHECK (backend = 'FUSEKI') NOT VALID;
