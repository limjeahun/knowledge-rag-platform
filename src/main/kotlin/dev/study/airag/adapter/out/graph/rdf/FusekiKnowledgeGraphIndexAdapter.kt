package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.adapter.out.ontology.owl.OwlOntologyCatalog
import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjectionReceipt
import dev.study.airag.domain.vo.DocumentId
import org.apache.jena.query.QuerySolution
import org.apache.jena.rdf.model.ModelFactory
import org.springframework.stereotype.Component

/**
 * 검증된 문서 그래프를 OWL/Fuseki named graph 프로젝션으로 원자적으로 교체한다.
 *
 * 저장 전에 Application projection을 asserted RDF와 statement provenance로 변환하고 SHACL
 * 검증 및 HermiT 추론을 수행한다. 하나의 Fuseki 쓰기 transaction에서 ontology, shapes,
 * asserted, inferred, provenance graph와 projection catalog를 함께 갱신한다. 성공 후에는
 * catalog가 가리키는 모든 활성 문서 graph를 합쳐 읽기 전용 active union graph를 재구성한다.
 */
@Component
class FusekiKnowledgeGraphIndexAdapter(
    private val gateway: RdfDatasetGateway,
    private val ontologyCatalog: OwlOntologyCatalog,
    private val mapper: RdfKnowledgeGraphProjectionMapper,
    private val validator: RdfKnowledgeGraphValidator,
    private val reasoner: OwlKnowledgeGraphReasoner,
) : KnowledgeGraphIndexPort {
    /**
     * 같은 문서의 이전 graph를 새 버전으로 교체하고 registry 저장에 필요한 receipt를 반환한다.
     *
     * RDF 매핑, SHACL 검증과 OWL 추론은 원격 쓰기 transaction 전에 완료한다. 이 단계가
     * 실패하면 Fuseki는 변경되지 않는다. 쓰기 transaction 안에서는 schema, shapes,
     * asserted, inferred, provenance와 catalog pointer를 함께 교체하고 활성 union을 재구성한다.
     *
     * @param projection Application 검증을 통과한 현재 문서 버전의 전체 그래프
     * @return PostgreSQL projection registry에 기록할 ontology 및 graph 식별 정보
     * @throws InvalidKnowledgeGraphExtractionException RDF가 SHACL 계약을 위반한 경우
     * @throws IllegalArgumentException OWL 일관성 또는 추론 statement 상한을 위반한 경우
     */
    override fun replace(projection: KnowledgeGraphProjection): KnowledgeGraphProjectionReceipt {
        val snapshot = ontologyCatalog.load()
        val names = RdfGraphNames.from(projection, snapshot.checksum)
        val mapped = mapper.map(projection)
        validator.validate(mapped)
        val reasoning = reasoner.infer(mapped.asserted)
        gateway.write { connection ->
            val previousGraphs = findDocumentGraphs(connection, projection.documentId)
            connection.put(names.ontology, snapshot.schemaModel)
            connection.put(names.shapes, ModelFactory.createModelForGraph(snapshot.shapesGraph))
            connection.put(names.asserted, mapped.asserted)
            connection.put(names.inferred, reasoning.inferred)
            connection.put(names.provenance, mapped.provenance.union(reasoning.provenance))
            replaceCatalogEntry(connection, projection, names, snapshot.checksum)
            previousGraphs
                .filterNot { it in names.documentGraphs() }
                .forEach(connection::delete)
            rebuildActiveGraphs(connection)
        }
        return KnowledgeGraphProjectionReceipt(
            documentId = projection.documentId,
            documentVersion = projection.documentVersion,
            ontologyIri =
                snapshot
                    .rootOntology.ontologyID.ontologyIRI
                    .orElseThrow()
                    .toString(),
            ontologyVersion = snapshot.version,
            ontologyChecksum = snapshot.checksum,
            graphNames = names.documentGraphs() + names.ontology + names.shapes,
            projectedAt = projection.projectedAt,
        )
    }

    /**
     * 문서 전용 graph와 catalog pointer를 같은 쓰기 transaction에서 제거한다.
     *
     * 삭제 후 모든 활성 union graph를 catalog 기준으로 다시 만들어 제거된 문서의 triple이
     * 조회 결과에 남지 않게 한다. ontology와 shapes graph는 다른 문서가 공유할 수 있어 지우지 않는다.
     *
     * @param documentId 제거할 원본 지식 문서의 Domain 식별자
     */
    override fun remove(documentId: DocumentId) {
        gateway.write { connection ->
            findDocumentGraphs(connection, documentId).forEach(connection::delete)
            connection.update(
                """
                PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
                DELETE WHERE {
                  GRAPH <${RdfKnowledgeGraphVocabulary.CATALOG_GRAPH}> {
                    ?projection core:documentId ${sparqlString(documentId.toString())} ;
                                ?predicate ?object .
                  }
                }
                """.trimIndent(),
            )
            rebuildActiveGraphs(connection)
        }
    }

    /**
     * projection catalog에서 한 문서가 현재 가리키는 문서 전용 graph IRI를 조회한다.
     *
     * catalog entry가 없으면 빈 Set을 반환하며 중복 IRI는 insertion-order Set에서 제거한다.
     * 호출자가 제공한 connection의 현재 transaction을 그대로 사용한다.
     *
     * @param connection 열린 Fuseki READ 또는 WRITE transaction
     * @param documentId 조회할 문서 식별자
     * @return asserted, inferred, provenance graph IRI의 중복 없는 집합
     */
    private fun findDocumentGraphs(
        connection: org.apache.jena.rdfconnection.RDFConnection,
        documentId: DocumentId,
    ): Set<String> {
        val graphs = linkedSetOf<String>()
        connection.querySelect(
            """
            PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
            SELECT ?asserted ?inferred ?provenance WHERE {
              GRAPH <${RdfKnowledgeGraphVocabulary.CATALOG_GRAPH}> {
                ?projection core:documentId ${sparqlString(documentId.toString())} ;
                            core:assertedGraph ?asserted ;
                            core:inferredGraph ?inferred ;
                            core:provenanceGraph ?provenance .
              }
            }
            """.trimIndent(),
        ) { row: QuerySolution ->
            graphs += row.getResource("asserted").uri
            graphs += row.getResource("inferred").uri
            graphs += row.getResource("provenance").uri
        }
        return graphs
    }

    /**
     * 한 문서의 기존 catalog statement를 지우고 새 활성 pointer를 삽입한다.
     *
     * DELETE와 INSERT는 호출자의 동일 WRITE transaction에서 실행된다. catalog는 실제 triple을
     * 복제하지 않고 문서 버전·ontology checksum과 세 문서 graph의 위치만 소유한다.
     *
     * @param connection 열린 Fuseki WRITE transaction
     * @param projection 현재 문서 ID, 버전과 ontology version
     * @param names 새로 저장한 named graph IRI
     * @param checksum TBox와 SHACL shapes의 전체 SHA-256
     */
    private fun replaceCatalogEntry(
        connection: org.apache.jena.rdfconnection.RDFConnection,
        projection: KnowledgeGraphProjection,
        names: RdfGraphNames,
        checksum: String,
    ) {
        val documentId = projection.documentId.toString()
        connection.update(
            """
            PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
            DELETE WHERE {
              GRAPH <${RdfKnowledgeGraphVocabulary.CATALOG_GRAPH}> {
                ?projection core:documentId ${sparqlString(documentId)} ;
                            ?predicate ?object .
              }
            };
            INSERT DATA {
              GRAPH <${RdfKnowledgeGraphVocabulary.CATALOG_GRAPH}> {
                <${names.projection}> a core:GraphProjection ;
                    core:documentId ${sparqlString(documentId)} ;
                    core:documentVersion ${projection.documentVersion} ;
                    core:ontologyVersion ${sparqlString(projection.ontologyVersion)} ;
                    core:assertedGraph <${names.asserted}> ;
                    core:inferredGraph <${names.inferred}> ;
                    core:provenanceGraph <${names.provenance}> ;
                    core:active true ;
                    core:checksum ${sparqlString(checksum)} .
              }
            }
            """.trimIndent(),
        )
    }

    /**
     * catalog에서 `active=true`인 문서 graph만 각 active union graph에 복사한다.
     *
     * 조회 Adapter가 문서별 graph 목록을 매번 조합하지 않게 하는 read-optimized projection이다.
     * 각 대상 graph를 먼저 비우고 catalog가 참조하는 source graph를 다시 복사하므로 반드시
     * 문서 graph 및 catalog 갱신과 같은 WRITE transaction에서 호출해야 한다.
     *
     * @param connection 문서 graph 변경을 포함한 현재 Fuseki WRITE transaction
     */
    private fun rebuildActiveGraphs(connection: org.apache.jena.rdfconnection.RDFConnection) {
        listOf(
            RdfKnowledgeGraphVocabulary.ASSERTED_GRAPH to RdfKnowledgeGraphVocabulary.ACTIVE_ASSERTED_GRAPH,
            RdfKnowledgeGraphVocabulary.INFERRED_GRAPH to RdfKnowledgeGraphVocabulary.ACTIVE_INFERRED_GRAPH,
            RdfKnowledgeGraphVocabulary.PROVENANCE_GRAPH to RdfKnowledgeGraphVocabulary.ACTIVE_PROVENANCE_GRAPH,
        ).forEach { (catalogProperty, activeGraph) ->
            connection.update("CLEAR SILENT GRAPH <$activeGraph>")
            connection.update(
                """
                PREFIX core: <${RdfKnowledgeGraphVocabulary.CORE_NAMESPACE}>
                INSERT {
                  GRAPH <$activeGraph> { ?subject ?predicate ?object }
                }
                WHERE {
                  GRAPH <${RdfKnowledgeGraphVocabulary.CATALOG_GRAPH}> {
                    ?projection core:active true ;
                                <${catalogProperty.uri}> ?sourceGraph .
                  }
                  GRAPH ?sourceGraph { ?subject ?predicate ?object }
                }
                """.trimIndent(),
            )
        }
    }

    /**
     * 외부 값을 SPARQL string literal lexical form으로 안전하게 이스케이프한다.
     *
     * backslash, quote와 줄바꿈을 처리하며 IRI 위치에는 사용하지 않는다. 문서 ID도 문자열
     * literal로 비교하여 SPARQL 구문 구조를 사용자 값이 바꾸지 못하게 한다.
     *
     * @param value literal에 넣을 원본 문자열
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
}
