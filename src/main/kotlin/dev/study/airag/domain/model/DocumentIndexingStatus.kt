package dev.study.airag.domain.model

/** 지식 문서가 등록된 후 검색 가능해지거나 삭제될 때까지의 업무 상태다. */
enum class DocumentIndexingStatus {
    /** 색인 요청이 접수되었지만 처리가 시작되지 않은 상태 */
    PENDING,

    /** 청킹과 검색 인덱스 교체가 진행 중인 상태 */
    INDEXING,

    /** 현재 문서 버전이 검색 가능한 상태 */
    INDEXED,

    /** 마지막 색인 시도가 실패하여 재시도를 요청할 수 있는 상태 */
    FAILED,

    /** 더 이상 검색하거나 색인할 수 없는 상태 */
    DELETED,
}
