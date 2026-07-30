package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import org.springframework.data.jpa.repository.JpaRepository

/** OWL version IRI를 기본 키로 ontology 배포 이력을 저장하는 Spring Data 경계다. */
interface KnowledgeOntologyVersionRepository : JpaRepository<KnowledgeOntologyVersionEntity, String>
