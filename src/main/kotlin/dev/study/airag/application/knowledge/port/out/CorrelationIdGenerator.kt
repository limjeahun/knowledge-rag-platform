package dev.study.airag.application.knowledge.port.out

import java.util.UUID

/** 새 비동기 업무 흐름을 추적할 상관관계 식별자를 생성한다. */
fun interface CorrelationIdGenerator {
    fun nextId(): UUID
}
