package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.dto.KnowledgeGraphAssertionKind
import dev.study.airag.application.graph.dto.query.FindRelevantKnowledgeGraphFactsQuery
import dev.study.airag.application.graph.dto.result.KnowledgeGraphFactResult
import dev.study.airag.application.graph.port.`in`.FindRelevantKnowledgeGraphFactsUseCase
import dev.study.airag.application.knowledge.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.knowledge.dto.query.SearchKnowledgeQuery
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.port.out.GenerateKnowledgeAnswerPort
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeAnswerGenerationRequest
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeIndexReplacement
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HybridKnowledgeQueryServiceTests {
    @Test
    fun `reuses vector sources and ontology graph facts for answer generation and response`() {
        val sources =
            listOf(
                KnowledgeSearchHit(
                    chunkId = "chunk-1",
                    documentId = "document-1",
                    documentVersion = 1,
                    chunkIndex = 0,
                    title = "Architecture",
                    content = "Indexer writes chunks to Milvus.",
                    score = 0.91,
                    metadata = emptyMap(),
                ),
            )
        val facts =
            listOf(
                KnowledgeGraphFactResult(
                    relationId = "relation-1",
                    ontologyVersion = "urn:airag:ontology:software-architecture:1.0.0",
                    assertionKind = KnowledgeGraphAssertionKind.INFERRED,
                    type = "USES",
                    sourceEntityId = "entity-1",
                    sourceName = "Indexer",
                    targetEntityId = "entity-2",
                    targetName = "Milvus",
                    evidence = emptyList(),
                ),
            )
        val answerPort = RecordingAnswerPort()
        var graphQuery: FindRelevantKnowledgeGraphFactsQuery? = null
        val service =
            QueryKnowledgeService(
                documentPort = EmptyDocumentPort,
                knowledgeIndexPort = FixedKnowledgeIndexPort(sources),
                generateAnswerPort = answerPort,
                graphFactsUseCase =
                    FindRelevantKnowledgeGraphFactsUseCase { query ->
                        graphQuery = query
                        facts
                    },
            )

        val result = service.answer(AnswerKnowledgeQuestionQuery("Indexer는 무엇을 사용하는가?"))

        assertEquals("hybrid answer", result.answer)
        assertSame(sources, result.sources)
        assertSame(facts, result.graphFacts)
        assertSame(sources, answerPort.request!!.sources)
        assertSame(facts, answerPort.request!!.graphFacts)
        assertEquals(listOf("chunk-1"), graphQuery?.seedChunkIds)
    }

    private class RecordingAnswerPort : GenerateKnowledgeAnswerPort {
        var request: KnowledgeAnswerGenerationRequest? = null

        override fun generate(
            question: String,
            sources: List<KnowledgeSearchHit>,
        ): String = error("parameter-object overload must be used")

        override fun generate(request: KnowledgeAnswerGenerationRequest): String {
            this.request = request
            return "hybrid answer"
        }
    }

    private class FixedKnowledgeIndexPort(
        private val sources: List<KnowledgeSearchHit>,
    ) : KnowledgeIndexPort {
        override fun replace(replacement: KnowledgeIndexReplacement) = Unit

        override fun search(query: SearchKnowledgeQuery): List<KnowledgeSearchHit> = sources

        override fun remove(documentId: DocumentId) = Unit
    }

    private object EmptyDocumentPort : KnowledgeDocumentPort {
        override fun save(document: KnowledgeDocument): KnowledgeDocument = document

        override fun findById(id: DocumentId): KnowledgeDocument? = null

        override fun findAll(): List<KnowledgeDocument> = emptyList()
    }
}
