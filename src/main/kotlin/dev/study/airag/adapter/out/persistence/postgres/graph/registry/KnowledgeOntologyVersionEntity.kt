package dev.study.airag.adapter.out.persistence.postgres.graph.registry

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 실제 projection 생성에 사용된 OWL ontology version과 checksum을 기록하는 JPA entity다.
 *
 * version IRI가 논리 식별자이고 checksum은 같은 IRI의 파일 내용 변조나 배포 오류를 감지한다.
 */
@Entity
@Table(name = "knowledge_ontology_versions")
class KnowledgeOntologyVersionEntity(
    @Id
    @Column(name = "version_iri", length = 500, nullable = false)
    val versionIri: String,
    @Column(name = "ontology_iri", length = 500, nullable = false)
    val ontologyIri: String,
    @Column(name = "checksum", length = 64, nullable = false)
    var checksum: String,
    @Column(name = "ontology_format", length = 20, nullable = false)
    var ontologyFormat: String,
    @Column(name = "status", length = 30, nullable = false)
    var status: String,
    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant,
)
