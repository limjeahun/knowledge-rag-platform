package dev.study.airag.application.exception

import java.util.UUID

/** 동일한 색인 요청을 다른 실행자가 처리 중이므로 현재 요청을 나중에 재시도해야 한다. */
class DocumentIndexingAlreadyInProgressException(
    eventId: UUID,
) : IllegalStateException("다른 작업자가 색인 이벤트를 처리 중입니다: $eventId")
