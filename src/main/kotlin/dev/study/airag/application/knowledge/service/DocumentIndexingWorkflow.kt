package dev.study.airag.application.knowledge.service

import dev.study.airag.application.graph.port.`in`.ProjectKnowledgeGraphUseCase
import dev.study.airag.application.knowledge.dto.command.IndexKnowledgeDocumentCommand
import dev.study.airag.application.knowledge.exception.DocumentIndexingFailedException
import dev.study.airag.application.knowledge.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.knowledge.port.out.ChunkKnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.DocumentIndexingCompletionPort
import dev.study.airag.application.knowledge.port.out.KnowledgeDocumentPort
import dev.study.airag.application.knowledge.port.out.KnowledgeIndexPort
import dev.study.airag.application.knowledge.port.out.dto.KnowledgeIndexReplacement
import dev.study.airag.domain.model.DocumentIndexingDecision
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 문서 상태, 검색 인덱스와 완료 기록을 하나의 색인 업무 흐름으로 조율한다. */
@Service
class DocumentIndexingWorkflow(
    private val documentPort: KnowledgeDocumentPort,
    private val chunkDocumentPort: ChunkKnowledgeDocumentPort,
    private val knowledgeIndexPort: KnowledgeIndexPort,
    private val projectKnowledgeGraphUseCase: ProjectKnowledgeGraphUseCase,
    private val completionPort: DocumentIndexingCompletionPort,
    private val clock: Clock,
) {
    /**
     * 이벤트별 PostgreSQL 처리 권한을 트랜잭션 종료까지 보유하고 문서 색인을 완료한다.
     *
     * 삭제된 문서, 이미 색인된 문서, 현재 버전과 다른 이벤트도 완료 기록을 남겨 재전달을 끝낸다.
     * 색인 시작 이후 실패하면 문서를 FAILED로 저장하되 완료 기록은 남기지 않아 재시도를 허용한다.
     */
    @Transactional(noRollbackFor = [DocumentIndexingFailedException::class])
    fun index(command: IndexKnowledgeDocumentCommand) {
        if (!completionPort.claim(command.eventId)) return

        val documentId = DocumentId.from(command.documentId)
        val document =
            documentPort.findById(documentId) ?: throw KnowledgeDocumentNotFoundException(command.documentId)

        when (document.decideIndexing(command.documentVersion)) {
            DocumentIndexingDecision.INDEX -> {
                Unit
            }

            DocumentIndexingDecision.ALREADY_INDEXED,
            DocumentIndexingDecision.VERSION_MISMATCH,
            DocumentIndexingDecision.DOCUMENT_DELETED,
            -> {
                markCompleted(command)
                return
            }
        }

        var started = false
        try {
            document.startIndexing(clock.instant())
            documentPort.save(document)
            started = true

            val chunks = chunkDocumentPort.chunk(document)
            require(chunks.isNotEmpty()) { "임베딩을 생성하기에 문서 내용이 너무 짧습니다." }
            knowledgeIndexPort.replace(
                KnowledgeIndexReplacement(
                    documentId = document.id,
                    documentVersion = document.version,
                    chunks = chunks,
                ),
            )
            /*
             * Milvus 청크와 지식 그래프는 모두 원문에서 재생성하는 검색 프로젝션이다.
             * 그래프 기능이 활성화된 경우 두 프로젝션이 모두 현재 문서 버전으로 교체되어야만
             * Aggregate를 INDEXED로 확정한다. 그래프 추출 실패를 무시하면 INDEXED의 의미가
             * 배포마다 달라지고 사용자는 오래된 관계를 최신 근거로 오해할 수 있다.
             */
            projectKnowledgeGraphUseCase.project(document, chunks)

            document.completeIndexing(clock.instant())
            documentPort.save(document)
            markCompleted(command)
        } catch (exception: Exception) {
            if (
                started &&
                document.failIndexingIfInProgress(exception.message ?: exception.javaClass.simpleName, clock.instant())
            ) {
                documentPort.save(document)
            }
            throw DocumentIndexingFailedException("문서 색인에 실패했습니다: ${document.id}", exception)
        }
    }

    private fun markCompleted(command: IndexKnowledgeDocumentCommand) {
        completionPort.complete(command.eventId, clock.instant())
    }
}
