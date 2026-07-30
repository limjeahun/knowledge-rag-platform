package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind
import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.query.GetKnowledgeEntityNeighborhoodQuery
import dev.study.airag.application.graph.dto.query.SearchKnowledgeGraphQuery
import dev.study.airag.application.graph.port.out.KnowledgeGraphQueryPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidenceView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphFactView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphNeighborhoodView
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphRelationView
import org.apache.jena.query.QuerySolution
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Fuseki active union graph를 SPARQL로 조회해 기술 독립적인 Application view로 변환한다.
 *
 * 개체 본문은 asserted graph에서 읽어 안정적인 이름과 식별자를 유지하고, 관계는 asserted와
 * inferred graph를 함께 조회한다. asserted 관계에는 원문 evidence를 결합하고 inferred 관계는
 * assertion kind만 표시하여 추론 결과에 존재하지 않는 quote를 만들지 않는다.
 */
@Component
class FusekiKnowledgeGraphQueryAdapter(
    private val gateway: RdfDatasetGateway,
) : KnowledgeGraphQueryPort {
    /**
     * 대표 이름·별칭의 대소문자 무시 부분 일치와 선택적 ontology code로 개체를 검색한다.
     *
     * 개체의 식별 정보는 active asserted graph에서만 읽어 추론 graph에 타입만 존재하는
     * 불완전한 개체가 결과에 들어오지 않게 한다. OPTIONAL alias 때문에 한 개체가 여러 행으로
     * 펼쳐질 수 있어 SPARQL에서는 넉넉히 조회한 뒤 entity ID로 합치고 최종 limit을 적용한다.
     *
     * @param query 정규화된 검색어, 선택적 type code와 반환 상한
     * @return 대표 이름 순으로 발견한 개체와 중복 제거된 별칭·원문 근거
     */
    override fun searchEntities(query: SearchKnowledgeGraphQuery): List<KnowledgeGraphEntityView> =
        gateway.read { connection ->
            val entities = linkedMapOf<String, MutableEntity>()
            val typeFilter =
                query.type?.let { "FILTER(?typeCode = ${sparqlString(it)})" }.orEmpty()
            connection.querySelect(
                """
                PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
                SELECT DISTINCT ?entity ?entityId ?ontologyVersion ?typeCode ?name ?alias WHERE {
                  GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH}> {
                    ?entity core:entityId ?entityId ;
                            core:ontologyVersion ?ontologyVersion ;
                            rdf:type ?entityType ;
                            skos:prefLabel ?name .
                    OPTIONAL { ?entity skos:altLabel ?alias }
                  }
                  GRAPH ?ontologyGraph {
                    ?entityType a owl:Class ;
                                core:code ?typeCode ;
                                core:extractable true .
                  }
                  FILTER(
                    CONTAINS(LCASE(STR(?name)), LCASE(${sparqlString(query.text)})) ||
                    (BOUND(?alias) && CONTAINS(LCASE(STR(?alias)), LCASE(${sparqlString(query.text)})))
                  )
                  $typeFilter
                }
                ORDER BY LCASE(STR(?name))
                LIMIT ${query.limit * 20}
                """.trimIndent(),
            ) { row ->
                val entityId = row.literal("entityId")
                val entity =
                    entities.getOrPut(entityId) {
                        MutableEntity(
                            entityId = entityId,
                            ontologyVersion = row.literal("ontologyVersion"),
                            type = row.literal("typeCode"),
                            name = row.literal("name"),
                        )
                    }
                row.optionalLiteral("alias")?.let(entity.aliases::add)
            }
            addEntityEvidence(connection, entities)
            entities.values.take(query.limit).map(MutableEntity::toView)
        }

    /**
     * 요청된 개체에서 breadth-first 방식으로 제한된 깊이와 건수만 탐색한다.
     *
     * 각 단계에서 현재 frontier에 인접한 asserted/inferred 관계를 조회한다. entity ID와
     * relation ID를 map key로 사용하므로 순환 그래프에서도 이미 방문한 항목을 다시 추가하지
     * 않는다. 중심 개체가 active asserted graph에 없으면 absence를 `null`로 표현한다.
     *
     * @param query 중심 entity ID, 1~2 hop 깊이와 전체 결과 상한
     * @return 중심 개체·발견 개체·관계의 제한된 이웃 또는 중심 개체가 없으면 `null`
     */
    override fun findNeighborhood(query: GetKnowledgeEntityNeighborhoodQuery): KnowledgeGraphNeighborhoodView? =
        gateway.read { connection ->
            val center = findEntity(connection, query.entityId) ?: return@read null
            val entities = linkedMapOf(center.entityId to center)
            val relations = linkedMapOf<String, KnowledgeGraphFactView>()
            var frontier = linkedSetOf(center.entityId)
            repeat(query.depth) {
                if (frontier.isEmpty() || entities.size >= query.limit || relations.size >= query.limit) {
                    return@repeat
                }
                val adjacent =
                    findRelations(
                        connection = connection,
                        entityIds = frontier,
                        textTokens = emptyList(),
                        limit = query.limit - relations.size,
                    )
                val next = linkedSetOf<String>()
                adjacent.forEach { relation ->
                    if (relations.size >= query.limit) return@forEach
                    relations.putIfAbsent(relation.relationId, relation)
                    listOf(relation.sourceEntityId, relation.targetEntityId).forEach { entityId ->
                        if (entityId !in entities && entities.size < query.limit) {
                            findEntity(connection, entityId)?.let { entity ->
                                entities[entityId] = entity
                                next += entityId
                            }
                        }
                    }
                }
                frontier = next
            }
            addEntityEvidence(connection, entities)
            KnowledgeGraphNeighborhoodView(
                center = entities.getValue(center.entityId).toView(),
                entities = entities.values.map(MutableEntity::toView),
                relations = relations.values.map { it.toRelationView() },
            )
        }

    /**
     * 질문 token이 source/target 이름 또는 관계 code와 일치하는 그래프 사실을 반환한다.
     *
     * 이 조회는 벡터 유사도가 아니라 가벼운 lexical GraphRAG 후보 검색이다. [searchableTokens]로
     * 질문을 제한된 token 집합으로 만들고 asserted와 inferred active graph를 함께 조회한다.
     *
     * @param query 자연어 질문과 Application이 제한한 최대 사실 수
     * @return assertion kind와 이용 가능한 직접 원문 근거가 포함된 관계 사실
     */
    override fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactView> =
        gateway.read { connection ->
            findRelations(
                connection = connection,
                entityIds = emptySet(),
                textTokens = searchableTokens(query.text),
                limit = query.limit,
            )
        }

    /**
     * active asserted graph에서 정확한 Application entity ID 하나를 조회한다.
     *
     * 타입 code는 저장된 rdf:type IRI와 현재 ontology graph의 `core:code`를 조인하여 얻는다.
     * alias별 여러 SPARQL 행은 하나의 mutable accumulator로 합친다. evidence는 별도 batch
     * 조회인 [addEntityEvidence]가 담당하므로 여기서는 identity와 label만 채운다.
     *
     * @param connection 현재 READ transaction의 connection
     * @param entityId `core:entityId` literal과 정확히 일치할 ID
     * @return 조회용 mutable 개체 또는 존재하지 않으면 `null`
     */
    private fun findEntity(
        connection: org.apache.jena.rdfconnection.RDFConnection,
        entityId: String,
    ): MutableEntity? {
        var entity: MutableEntity? = null
        connection.querySelect(
            """
            PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            SELECT DISTINCT ?ontologyVersion ?typeCode ?name ?alias WHERE {
              GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH}> {
                ?entity core:entityId ${sparqlString(entityId)} ;
                        core:ontologyVersion ?ontologyVersion ;
                        rdf:type ?entityType ;
                        skos:prefLabel ?name .
                OPTIONAL { ?entity skos:altLabel ?alias }
              }
              GRAPH ?ontologyGraph {
                ?entityType a owl:Class ;
                            core:code ?typeCode ;
                            core:extractable true .
              }
            }
            """.trimIndent(),
        ) { row ->
            val current =
                entity ?: MutableEntity(
                    entityId = entityId,
                    ontologyVersion = row.literal("ontologyVersion"),
                    type = row.literal("typeCode"),
                    name = row.literal("name"),
                ).also { entity = it }
            row.optionalLiteral("alias")?.let(current.aliases::add)
        }
        return entity
    }

    /**
     * active asserted/inferred graph에서 조건에 맞는 방향성 object-property 사실을 조회한다.
     *
     * [entityIds]가 있으면 어느 한 끝점이 해당 ID인 인접 관계를, [textTokens]가 있으면
     * 끝점 이름이나 relation code가 token을 포함하는 관계를 찾는다. 두 조건이 모두 비어
     * 있으면 상한 내 전체 관계 후보를 조회한다. 같은 triple의 provenance 행은 statement
     * key로 합치고 직접 근거만 중복 없이 수집한다.
     *
     * inferred graph의 사실은 provenance에 document/quote가 없으므로 evidence가 빈 목록일 수
     * 있다. relation ID가 없는 추론 사실은 triple 문자열로 결정적 ID를 만들어 API 안정성을
     * 유지한다.
     *
     * @param connection 현재 READ transaction의 connection
     * @param entityIds 이웃 조회에 사용할 entity ID 집합, 필터하지 않으면 빈 집합
     * @param textTokens lexical 관련성 검색 token, 필터하지 않으면 빈 목록
     * @param limit 반환할 중복 제거 관계의 최대 수
     * @return asserted와 inferred를 구분한 기술 중립 그래프 사실
     */
    private fun findRelations(
        connection: org.apache.jena.rdfconnection.RDFConnection,
        entityIds: Set<String>,
        textTokens: List<String>,
        limit: Int,
    ): List<KnowledgeGraphFactView> {
        val byStatement = linkedMapOf<String, MutableFact>()
        val entityFilter =
            if (entityIds.isEmpty()) {
                ""
            } else {
                val values = entityIds.joinToString(" ") { sparqlString(it) }
                "VALUES ?requestedEntityId { $values } FILTER(?sourceEntityId = ?requestedEntityId || ?targetEntityId = ?requestedEntityId)"
            }
        val textFilter =
            if (textTokens.isEmpty()) {
                ""
            } else {
                val conditions =
                    textTokens.joinToString(" || ") { token ->
                        "CONTAINS(LCASE(STR(?sourceName)), LCASE(${sparqlString(token)})) || " +
                            "CONTAINS(LCASE(STR(?targetName)), LCASE(${sparqlString(token)})) || " +
                            "CONTAINS(LCASE(STR(?relationType)), LCASE(${sparqlString(token)}))"
                    }
                "FILTER($conditions)"
            }
        connection.querySelect(
            """
            PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            SELECT DISTINCT ?graph ?source ?predicate ?target ?sourceEntityId ?sourceName
                            ?targetEntityId ?targetName ?ontologyVersion ?relationType
                            ?relationId ?assertionKind ?documentId ?documentVersion
                            ?chunkId ?quote ?confidence WHERE {
              VALUES ?graph {
                <${RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH}>
                <${RdfKnowledgeGraphVocabulary.ACTIVE_INFERRED_GRAPH}>
              }
              GRAPH ?graph { ?source ?predicate ?target }
              GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH}> {
                ?source core:entityId ?sourceEntityId ;
                        core:ontologyVersion ?ontologyVersion ;
                        skos:prefLabel ?sourceName .
                ?target core:entityId ?targetEntityId ;
                        skos:prefLabel ?targetName .
              }
              GRAPH ?ontologyGraph {
                ?predicate a owl:ObjectProperty ;
                           core:code ?relationType ;
                           core:extractable true .
              }
              OPTIONAL {
                GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_PROVENANCE_GRAPH}> {
                  ?assertion rdf:subject ?source ;
                             rdf:predicate ?predicate ;
                             rdf:object ?target ;
                             core:assertionKind ?assertionKind .
                  OPTIONAL { ?assertion core:relationId ?relationId }
                  OPTIONAL {
                    ?assertion core:documentId ?documentId ;
                               core:documentVersion ?documentVersion ;
                               core:chunkId ?chunkId ;
                               core:quote ?quote ;
                               core:confidence ?confidence .
                  }
                }
              }
              $entityFilter
              $textFilter
            }
            LIMIT ${limit * 20}
            """.trimIndent(),
        ) { row ->
            val statementKey =
                "${row.resourceUri("source")}|${row.resourceUri("predicate")}|${row.resourceUri("target")}"
            val inferred =
                row.resourceUri("graph") == RdfKnowledgeGraphVocabulary.ACTIVE_INFERRED_GRAPH
            val fact =
                byStatement.getOrPut(statementKey) {
                    MutableFact(
                        relationId =
                            row.optionalLiteral("relationId")
                                ?: stableUuid(statementKey).toString(),
                        ontologyVersion = row.literal("ontologyVersion"),
                        assertionKind =
                            if (inferred) {
                                KnowledgeGraphAssertionKind.INFERRED
                            } else {
                                KnowledgeGraphAssertionKind.ASSERTED
                            },
                        type = row.literal("relationType"),
                        sourceEntityId = row.literal("sourceEntityId"),
                        sourceName = row.literal("sourceName"),
                        targetEntityId = row.literal("targetEntityId"),
                        targetName = row.literal("targetName"),
                    )
                }
            row.toEvidenceOrNull()?.let { evidence ->
                if (evidence !in fact.evidence) fact.evidence += evidence
            }
        }
        return byStatement.values.take(limit).map(MutableFact::toView)
    }

    /**
     * 여러 개체의 rdf:type assertion provenance를 한 번의 SPARQL로 조회해 결합한다.
     *
     * N개 개체마다 개별 evidence query를 실행하는 N+1 문제를 피한다. asserted type statement에
     * 연결된 document, version, chunk, quote, confidence만 사용하며 추론 타입에는 가짜
     * evidence를 붙이지 않는다. 빈 입력은 원격 호출 없이 즉시 반환한다.
     *
     * @param connection 현재 READ transaction의 connection
     * @param entities entity ID별 mutable 조회 accumulator
     */
    private fun addEntityEvidence(
        connection: org.apache.jena.rdfconnection.RDFConnection,
        entities: Map<String, MutableEntity>,
    ) {
        if (entities.isEmpty()) return
        val values = entities.keys.joinToString(" ") { sparqlString(it) }
        connection.querySelect(
            """
            PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT DISTINCT ?entityId ?documentId ?documentVersion ?chunkId ?quote ?confidence WHERE {
              GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH}> {
                ?entity core:entityId ?entityId .
              }
              VALUES ?entityId { $values }
              GRAPH <${RdfKnowledgeGraphVocabulary.ACTIVE_PROVENANCE_GRAPH}> {
                ?assertion rdf:subject ?entity ;
                           rdf:predicate rdf:type ;
                           core:documentId ?documentId ;
                           core:documentVersion ?documentVersion ;
                           core:chunkId ?chunkId ;
                           core:quote ?quote ;
                           core:confidence ?confidence .
              }
            }
            """.trimIndent(),
        ) { row ->
            row.toEvidenceOrNull()?.let { entities[row.literal("entityId")]?.evidence?.add(it) }
        }
    }

    /**
     * 자연어 질문을 SPARQL `CONTAINS` 조건에 사용할 제한된 lexical token으로 정규화한다.
     *
     * 문자·숫자·underscore·hyphen 경계만 보존하고 한 글자 token을 제외한다. query 크기와
     * 조건 폭증을 막기 위해 중복 제거 후 최대 8개만 사용한다. 유효 token이 없으면 trim한
     * 원문 하나를 fallback으로 사용한다.
     *
     * @param text 사용자가 입력한 자연어 질문
     * @return 순서를 보존한 최대 8개의 검색 token
     */
    private fun searchableTokens(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}_-]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(8)
            .ifEmpty { listOf(text.trim()) }

    /**
     * OPTIONAL provenance 바인딩이 완전한 직접 원문 근거이면 Application evidence로 변환한다.
     *
     * `documentId`가 없다는 것은 inferred statement이거나 근거가 없는 행이라는 뜻이므로
     * `null`을 반환한다. 저장 계약상 documentId가 있으면 나머지 필드는 모두 필수이며,
     * 누락되었다면 Jena 접근 예외로 손상된 projection을 드러낸다.
     *
     * @return 직접 원문 근거 또는 provenance가 없으면 `null`
     */
    private fun QuerySolution.toEvidenceOrNull(): KnowledgeGraphEvidenceView? {
        if (!contains("documentId")) return null
        return KnowledgeGraphEvidenceView(
            documentId = literal("documentId"),
            documentVersion = getLiteral("documentVersion").long,
            chunkId = literal("chunkId"),
            quote = literal("quote"),
            confidence = getLiteral("confidence").double,
        )
    }

    /**
     * 필수 SPARQL literal 바인딩을 문자열로 읽는다.
     *
     * 필드가 없거나 literal이 아니면 기본값으로 숨기지 않고 Jena 예외를 전파한다.
     */
    private fun QuerySolution.literal(name: String): String = getLiteral(name).string

    /**
     * OPTIONAL SPARQL 바인딩이 존재하고 literal일 때만 문자열을 반환한다.
     *
     * @return literal 값 또는 미바인딩·비 literal이면 `null`
     */
    private fun QuerySolution.optionalLiteral(name: String): String? =
        if (contains(name) && get(name).isLiteral) getLiteral(name).string else null

    /** 필수 SPARQL resource 바인딩의 절대 IRI를 반환한다. */
    private fun QuerySolution.resourceUri(name: String): String = getResource(name).uri

    /**
     * GraphRAG 내부 fact view를 이웃 조회용 relation view로 손실 없이 변환한다.
     *
     * assertion kind와 evidence를 유지하여 REST 응답에서도 직접 진술과 추론을 구분한다.
     */
    private fun KnowledgeGraphFactView.toRelationView() =
        KnowledgeGraphRelationView(
            relationId = relationId,
            ontologyVersion = ontologyVersion,
            type = type,
            sourceEntityId = sourceEntityId,
            sourceName = sourceName,
            targetEntityId = targetEntityId,
            targetName = targetName,
            evidence = evidence,
            assertionKind = assertionKind,
        )

    /**
     * 사용자 검색어나 entity ID를 SPARQL 문자열 literal로 안전하게 이스케이프한다.
     *
     * 동적 값은 쿼리 구조나 IRI 위치에 삽입하지 않고 이 메서드가 만든 literal로만 사용한다.
     *
     * @return 양쪽 큰따옴표를 포함한 SPARQL 문자열 literal
     */
    private fun sparqlString(value: String): String =
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") +
            "\""

    /** provenance relation ID가 없는 추론 triple에 재현 가능한 fallback UUID를 부여한다. */
    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private data class MutableEntity(
        val entityId: String,
        val ontologyVersion: String,
        val type: String,
        val name: String,
        val aliases: MutableSet<String> = linkedSetOf(),
        val evidence: MutableList<KnowledgeGraphEvidenceView> = mutableListOf(),
    ) {
        /**
         * SPARQL 여러 행에서 누적한 별칭과 evidence를 불변 Port view로 고정한다.
         *
         * 동일 provenance가 조인으로 반복될 수 있으므로 evidence는 마지막에 중복 제거한다.
         */
        fun toView() =
            KnowledgeGraphEntityView(
                entityId = entityId,
                ontologyVersion = ontologyVersion,
                type = type,
                name = name,
                aliases = aliases,
                evidence = evidence.distinct(),
            )
    }

    private data class MutableFact(
        val relationId: String,
        val ontologyVersion: String,
        val assertionKind: KnowledgeGraphAssertionKind,
        val type: String,
        val sourceEntityId: String,
        val sourceName: String,
        val targetEntityId: String,
        val targetName: String,
        val evidence: MutableList<KnowledgeGraphEvidenceView> = mutableListOf(),
    ) {
        /** 한 triple에 누적된 assertion kind와 원문 근거를 불변 GraphRAG fact로 고정한다. */
        fun toView() =
            KnowledgeGraphFactView(
                relationId = relationId,
                ontologyVersion = ontologyVersion,
                assertionKind = assertionKind,
                type = type,
                sourceEntityId = sourceEntityId,
                sourceName = sourceName,
                targetEntityId = targetEntityId,
                targetName = targetName,
                evidence = evidence,
            )
    }
}
