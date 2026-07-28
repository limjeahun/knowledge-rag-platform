package dev.study.airag.application.graph.service

import dev.study.airag.application.graph.policy.KnowledgeGraphProjectionPolicy
import dev.study.airag.application.graph.port.`in`.ProjectKnowledgeGraphUseCase
import dev.study.airag.application.graph.port.out.ExtractKnowledgeGraphPort
import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.KnowledgeOntologyPort
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphExtractionRequest
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphProjection
import dev.study.airag.application.graph.validation.KnowledgeGraphExtractionBatch
import dev.study.airag.application.graph.validation.KnowledgeGraphExtractionValidator
import dev.study.airag.application.graph.validation.KnowledgeGraphValidationRequest
import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * 문서 청크를 ontology 기반 지식 그래프 프로젝션으로 만드는 애플리케이션 서비스다.
 *
 * 추출은 LLM이라는 비결정적 외부 능력이지만, 허용 타입·원문 인용 검증·문서 버전 교체는
 * 결정적인 애플리케이션 규칙이다. 이 구분으로 AI 모델이나 저장 기술이 바뀌어도 그래프의
 * 신뢰 계약은 유지된다.
 */
@Service
class ProjectKnowledgeGraphService(
    private val ontologyPort: KnowledgeOntologyPort,
    private val extractKnowledgeGraphPort: ExtractKnowledgeGraphPort,
    private val knowledgeGraphIndexPort: KnowledgeGraphIndexPort,
    private val validator: KnowledgeGraphExtractionValidator,
    private val policy: KnowledgeGraphProjectionPolicy,
    private val clock: Clock,
) : ProjectKnowledgeGraphUseCase {
    /**
     * 기능이 활성화된 경우에만 그래프를 생성하고 현재 문서 버전의 프로젝션을 교체한다.
     *
     * 활성화 상태에서는 예외를 삼키지 않는다. Milvus와 그래프 중 하나만 최신인 상태를
     * INDEXED로 확정하지 않도록 호출자가 같은 색인 실패 흐름으로 처리하게 한다.
     */
    override fun project(
        document: KnowledgeDocument,
        chunks: List<KnowledgeChunk>,
    ) {
        if (!policy.enabled) return

        val ontology = ontologyPort.load()
        val batches =
            chunks.chunked(policy.chunksPerRequest).map { batchChunks ->
                KnowledgeGraphExtractionBatch(
                    chunks = batchChunks,
                    extraction =
                        extractKnowledgeGraphPort.extract(
                            KnowledgeGraphExtractionRequest(
                                documentId = document.id,
                                documentVersion = document.version,
                                title = document.title,
                                ontology = ontology,
                                chunks = batchChunks,
                            ),
                        ),
                )
            }
        val graph =
            validator.validateAndMerge(
                KnowledgeGraphValidationRequest(
                    ontology = ontology,
                    batches = batches,
                    policy = policy,
                ),
            )
        knowledgeGraphIndexPort.replace(
            KnowledgeGraphProjection(
                documentId = document.id,
                documentVersion = document.version,
                ontologyVersion = ontology.version,
                entities = graph.entities,
                relations = graph.relations,
                projectedAt = clock.instant(),
            ),
        )
    }
}
