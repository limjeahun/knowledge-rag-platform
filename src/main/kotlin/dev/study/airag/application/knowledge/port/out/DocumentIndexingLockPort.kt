package dev.study.airag.application.knowledge.port.out

import java.util.UUID

/** 색인 완료 기록이 확정되기 전 같은 이벤트의 동시 실행을 줄인다. */
interface DocumentIndexingLockPort {
    /** 처리 권한을 획득했을 때만 해제에 사용할 lease를 반환한다. */
    fun tryAcquire(eventId: UUID): DocumentIndexingLease?

    /** 자신이 획득한 [lease]와 저장된 소유자가 일치할 때만 처리 권한을 해제한다. */
    fun release(lease: DocumentIndexingLease)
}
