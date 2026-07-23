package dev.study.airag.application.port.out

import java.time.Instant
import java.util.UUID

/** 같은 색인 이벤트의 업무 처리가 한 번만 완료되도록 영구 완료 사실을 관리한다. */
interface DocumentIndexingCompletionPort {
    /** 현재 트랜잭션에서 이벤트 처리 권한을 확보하며 이미 완료된 이벤트면 `false`를 반환한다. */
    fun claim(eventId: UUID): Boolean

    /** 이벤트의 업무 처리가 완료됐음을 영구 기록한다. */
    fun complete(
        eventId: UUID,
        completedAt: Instant,
    )
}
