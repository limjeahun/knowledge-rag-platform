package dev.study.airag.domain.exception

/** 허용되지 않은 문서 색인 상태 전이를 시도했음을 나타낸다. */
class InvalidDocumentStateTransitionException(
    message: String,
) : IllegalStateException(message)
