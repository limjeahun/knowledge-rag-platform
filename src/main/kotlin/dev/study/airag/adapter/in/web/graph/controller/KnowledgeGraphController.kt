package dev.study.airag.adapter.`in`.web.graph.controller

import dev.study.airag.adapter.`in`.web.graph.mapper.toQuery
import dev.study.airag.adapter.`in`.web.graph.mapper.toResponse
import dev.study.airag.adapter.`in`.web.graph.request.GetKnowledgeEntityNeighborhoodRequest
import dev.study.airag.adapter.`in`.web.graph.request.SearchKnowledgeGraphRequest
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphEntityResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphNeighborhoodResponse
import dev.study.airag.application.graph.port.`in`.GetKnowledgeEntityNeighborhoodUseCase
import dev.study.airag.application.graph.port.`in`.SearchKnowledgeGraphUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 지식 그래프의 읽기 전용 HTTP 경계다.
 *
 * Controller는 탐색 규칙이나 JPA 조회를 직접 수행하지 않고 요청 값을 Application Query로
 * 옮긴다. 그래프 변경은 문서 색인 파이프라인에서만 일어나므로 별도 쓰기 API를 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/graph")
class KnowledgeGraphController(
    private val searchUseCase: SearchKnowledgeGraphUseCase,
    private val neighborhoodUseCase: GetKnowledgeEntityNeighborhoodUseCase,
) : KnowledgeGraphSpec {
    @GetMapping("/entities")
    override fun searchEntities(
        @Valid @ModelAttribute request: SearchKnowledgeGraphRequest,
    ): List<KnowledgeGraphEntityResponse> = searchUseCase.search(request.toQuery()).map { it.toResponse() }

    @GetMapping("/entities/{entityId}/neighborhood")
    override fun getNeighborhood(
        @PathVariable entityId: String,
        @Valid @ModelAttribute request: GetKnowledgeEntityNeighborhoodRequest,
    ): KnowledgeGraphNeighborhoodResponse =
        neighborhoodUseCase
            .getNeighborhood(request.toQuery(entityId))
            .toResponse()
}
