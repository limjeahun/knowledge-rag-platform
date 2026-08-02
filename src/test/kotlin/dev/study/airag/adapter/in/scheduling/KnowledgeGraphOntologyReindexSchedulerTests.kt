package dev.study.airag.adapter.`in`.scheduling

import dev.study.airag.application.graph.dto.result.OntologyReindexRequestResult
import dev.study.airag.application.graph.port.`in`.ReindexKnowledgeDocumentsForOntologyUseCase
import dev.study.airag.config.graph.KnowledgeGraphProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KnowledgeGraphOntologyReindexSchedulerTests {
    @Test
    fun `disabled scheduler does not request ontology reindexing`() {
        var calls = 0
        val scheduler =
            KnowledgeGraphOntologyReindexScheduler(
                reindexUseCase =
                    ReindexKnowledgeDocumentsForOntologyUseCase {
                        calls++
                        OntologyReindexRequestResult("ontology", emptyList(), emptyList())
                    },
                properties = KnowledgeGraphProperties(enabled = true, ontologyReindexEnabled = false),
                meterRegistry = SimpleMeterRegistry(),
            )

        scheduler.requestStaleProjectionReindexing()

        assertEquals(0, calls)
    }

    @Test
    fun `enabled scheduler applies batch size and records requested and skipped counters`() {
        var requestedLimit = 0
        val meterRegistry = SimpleMeterRegistry()
        val scheduler =
            KnowledgeGraphOntologyReindexScheduler(
                reindexUseCase =
                    ReindexKnowledgeDocumentsForOntologyUseCase { command ->
                        requestedLimit = command.limit
                        OntologyReindexRequestResult(
                            ontologyVersion = "ontology-v2",
                            requestedDocumentIds = listOf("document-1", "document-2"),
                            skippedDocumentIds = listOf("document-3"),
                        )
                    },
                properties =
                    KnowledgeGraphProperties(
                        enabled = true,
                        ontologyReindexEnabled = true,
                        ontologyReindexBatchSize = 25,
                    ),
                meterRegistry = meterRegistry,
            )

        scheduler.requestStaleProjectionReindexing()

        assertEquals(25, requestedLimit)
        assertEquals(2.0, meterRegistry.counter("knowledge.graph.ontology.reindex.requested").count())
        assertEquals(1.0, meterRegistry.counter("knowledge.graph.ontology.reindex.skipped").count())
    }
}
