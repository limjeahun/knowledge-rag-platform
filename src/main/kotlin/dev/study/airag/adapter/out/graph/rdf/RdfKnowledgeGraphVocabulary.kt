package dev.study.airag.adapter.out.graph.rdf

import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS

/**
 * OWL, RDF, PROV-O, SKOS와 AIRAG graph catalog에서 사용하는 IRI의 단일 코드 정의다.
 *
 * Application/Domain에는 Jena 타입을 노출하지 않고 RDF Adapter 내부에서만 사용한다.
 * namespace와 active graph IRI를 한곳에 고정해 mapper·reasoner·SPARQL query 사이의 오타와
 * 서로 다른 graph 계약 생성을 방지한다.
 */
object RdfKnowledgeGraphVocabulary {
    const val CORE_NAMESPACE = "urn:airag:ontology:knowledge-core#"
    const val PROJECTION_NAMESPACE = "urn:airag:projection:"
    const val ENTITY_NAMESPACE = "urn:airag:entity:"
    const val ASSERTION_NAMESPACE = "urn:airag:assertion:"
    const val ACTIVITY_NAMESPACE = "urn:airag:activity:"
    const val DOCUMENT_NAMESPACE = "urn:airag:document:"
    const val CHUNK_NAMESPACE = "urn:airag:chunk:"

    const val CATALOG_GRAPH = "urn:airag:graph:projection-catalog"
    const val ACTIVE_ASSERTED_GRAPH = "urn:airag:graph:active-asserted"
    const val ACTIVE_INFERRED_GRAPH = "urn:airag:graph:active-inferred"
    const val ACTIVE_PROVENANCE_GRAPH = "urn:airag:graph:active-provenance"

    const val PROV_NAMESPACE = "http://www.w3.org/ns/prov#"
    const val SKOS_NAMESPACE = "http://www.w3.org/2004/02/skos/core#"

    val CODE = ResourceFactory.createProperty("${CORE_NAMESPACE}code")
    val EXTRACTABLE = ResourceFactory.createProperty("${CORE_NAMESPACE}extractable")
    val ENTITY_ID = ResourceFactory.createProperty("${CORE_NAMESPACE}entityId")
    val RELATION_ID = ResourceFactory.createProperty("${CORE_NAMESPACE}relationId")
    val DOCUMENT_ID = ResourceFactory.createProperty("${CORE_NAMESPACE}documentId")
    val DOCUMENT_VERSION = ResourceFactory.createProperty("${CORE_NAMESPACE}documentVersion")
    val CHUNK_ID = ResourceFactory.createProperty("${CORE_NAMESPACE}chunkId")
    val QUOTE = ResourceFactory.createProperty("${CORE_NAMESPACE}quote")
    val CONFIDENCE = ResourceFactory.createProperty("${CORE_NAMESPACE}confidence")
    val ASSERTION_KIND = ResourceFactory.createProperty("${CORE_NAMESPACE}assertionKind")
    val ONTOLOGY_VERSION = ResourceFactory.createProperty("${CORE_NAMESPACE}ontologyVersion")

    val STATEMENT_EVIDENCE = ResourceFactory.createResource("${CORE_NAMESPACE}StatementEvidence")
    val INFERRED_STATEMENT_PROVENANCE =
        ResourceFactory.createResource("${CORE_NAMESPACE}InferredStatementProvenance")

    val PREF_LABEL = ResourceFactory.createProperty("${SKOS_NAMESPACE}prefLabel")
    val ALT_LABEL = ResourceFactory.createProperty("${SKOS_NAMESPACE}altLabel")

    val PROV_ACTIVITY = ResourceFactory.createResource("${PROV_NAMESPACE}Activity")
    val WAS_DERIVED_FROM = ResourceFactory.createProperty("${PROV_NAMESPACE}wasDerivedFrom")
    val WAS_GENERATED_BY = ResourceFactory.createProperty("${PROV_NAMESPACE}wasGeneratedBy")
    val GENERATED_AT_TIME = ResourceFactory.createProperty("${PROV_NAMESPACE}generatedAtTime")

    val GRAPH_PROJECTION = ResourceFactory.createResource("${CORE_NAMESPACE}GraphProjection")
    val ASSERTED_GRAPH = ResourceFactory.createProperty("${CORE_NAMESPACE}assertedGraph")
    val INFERRED_GRAPH = ResourceFactory.createProperty("${CORE_NAMESPACE}inferredGraph")
    val PROVENANCE_GRAPH = ResourceFactory.createProperty("${CORE_NAMESPACE}provenanceGraph")
    val ACTIVE = ResourceFactory.createProperty("${CORE_NAMESPACE}active")
    val CHECKSUM = ResourceFactory.createProperty("${CORE_NAMESPACE}checksum")

    val RDF_TYPE = RDF.type
    val RDF_STATEMENT = RDF.Statement
    val RDF_SUBJECT = RDF.subject
    val RDF_PREDICATE = RDF.predicate
    val RDF_OBJECT = RDF.`object`
    val OWL_CLASS = OWL.Class
    val OWL_OBJECT_PROPERTY = OWL.ObjectProperty
    val RDFS_COMMENT = RDFS.comment
}
