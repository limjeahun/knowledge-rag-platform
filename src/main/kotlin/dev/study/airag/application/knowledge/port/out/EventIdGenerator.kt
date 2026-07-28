package dev.study.airag.application.knowledge.port.out

import java.util.UUID

/** 저장 및 전달 과정 전체에서 동일하게 사용할 고유 이벤트 식별자를 생성한다. */
fun interface EventIdGenerator {
    fun nextId(): UUID
}
