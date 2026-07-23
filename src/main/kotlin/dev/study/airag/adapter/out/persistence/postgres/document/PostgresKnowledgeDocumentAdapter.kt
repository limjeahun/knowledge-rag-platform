package dev.study.airag.adapter.out.persistence.postgres.document

import dev.study.airag.application.port.out.KnowledgeDocumentPort
import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId
import org.springframework.stereotype.Component

/** 원본 내용과 색인 상태를 문서의 기준 이력으로 저장한다. */
@Component
class PostgresKnowledgeDocumentAdapter(
    private val repository: KnowledgeDocumentRepository,
    private val mapper: KnowledgeDocumentMapper,
) : KnowledgeDocumentPort {
    /**
     * 최초 등록에는 새 문서 행을 만들고, 이후 상태 전이에는 같은 문서 행을 갱신한다.
     *
     * 문서 식별자와 최초 등록 시각은 상태가 변경되어도 유지한다.
     */
    override fun save(document: KnowledgeDocument): KnowledgeDocument {
        repository.save(mapper.toEntity(document))
        return document
    }

    /** 해당 식별자의 문서가 없으면 `null`을 반환한다. */
    override fun findById(id: DocumentId): KnowledgeDocument? =
        repository.findById(id.value).map(mapper::toDomain).orElse(null)
}
