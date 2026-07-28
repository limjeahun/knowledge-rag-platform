package dev.study.airag.application.knowledge.port.`in`

import dev.study.airag.application.knowledge.dto.command.RegisterKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.dto.result.RegisteredKnowledgeDocumentResult

fun interface RegisterKnowledgeDocumentUseCase {
    /**
     * 원본 문서 등록과 최초 색인 요청을 함께 접수한다.
     *
     * 제목 또는 본문이 공백이면 등록하지 않는다.
     */
    fun register(command: RegisterKnowledgeDocumentCommand): RegisteredKnowledgeDocumentResult
}
