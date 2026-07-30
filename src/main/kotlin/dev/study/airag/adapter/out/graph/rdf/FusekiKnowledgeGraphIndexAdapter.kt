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
     * SHACL 또는 OWL 일관성 검사가 실패하면 Fuseki transaction을 시작하기 전에 중단한다.
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

    /** 문서 전용 graph와 catalog pointer를 제거한 뒤 활성 union graph를 다시 물질화한다. */
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

    private fun sparqlString(value: String): String =
        "\"" +
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") +
            "\""
}
