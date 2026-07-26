package dev.study.airag.application.port.out

import dev.study.airag.domain.model.KnowledgeDocument
import dev.study.airag.domain.vo.DocumentId

/** 원본 문서와 색인 상태를 영구 보존한다. */
interface KnowledgeDocumentPort {
    /** 문서의 원본, 버전 및 현재 색인 상태를 함께 저장한다. */
    fun save(document: KnowledgeDocument): KnowledgeDocument

    /** 
     * 문서의 원본, 버전 및 현재 색인 조회
     * 등록된 문서가 없으면 `null`을 반환한다. 
     */
    fun findById(id: DocumentId): KnowledgeDocument?
        
    /**
     * 모든 문서를 조회한다.
     */
    fun findAll(): List<KnowledgeDocument>
        
}
