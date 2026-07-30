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
    /** 이름·별칭의 부분 일치와 선택적 ontology code로 asserted 개체를 검색한다. */
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
     * 순환 그래프에서도 이미 방문한 개체와 관계를 다시 추가하지 않는다.
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

    /** 질문 token이 개체 이름 또는 관계 code와 일치하는 asserted/inferred 사실을 반환한다. */
    override fun findRelevantFacts(query: FindRelevantKnowledgeGraphFactsQuery): List<KnowledgeGraphFactView> =
        gateway.read { connection ->
            findRelations(
                connection = connection,
                entityIds = emptySet(),
                textTokens = searchableTokens(query.text),
                limit = query.limit,
            )
        }

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

    private fun searchableTokens(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}_-]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(8)
            .ifEmpty { listOf(text.trim()) }

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

    private fun QuerySolution.literal(name: String): String = getLiteral(name).string

    private fun QuerySolution.optionalLiteral(name: String): String? =
        if (contains(name) && get(name).isLiteral) getLiteral(name).string else null

    private fun QuerySolution.resourceUri(name: String): String = getResource(name).uri

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

    private fun sparqlString(value: String): String =
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") +
            "\""

    private fun stableUuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    private data class MutableEntity(
        val entityId: String,
        val ontologyVersion: String,
        val type: String,
        val name: String,
        val aliases: MutableSet<String> = linkedSetOf(),
        val evidence: MutableList<KnowledgeGraphEvidenceView> = mutableListOf(),
    ) {
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
