package dev.study.airag.application.port.`in`

import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand

fun interface DeleteKnowledgeDocumentUseCase {
    /**
     * 문서를 검색 불가능한 상태로 전환하고 남아 있는 검색 근거를 제거한다.
     *
     * 이미 삭제된 문서에 대한 호출은 성공하지만 등록되지 않은 문서는 삭제할 수 없다.
     */
    fun delete(command: DeleteKnowledgeDocumentCommand)
}
