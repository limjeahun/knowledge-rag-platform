package dev.study.airag.application.knowledge.exception

/**
 * 문서가 검색 가능한 상태에 도달하지 못했음을 나타낸다.
 *
 * 실패 원인은 문서에 기록되며, 이 예외는 메시지 재전달 판단을 위해 호출자에게 전파된다.
 */
class DocumentIndexingFailedException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
