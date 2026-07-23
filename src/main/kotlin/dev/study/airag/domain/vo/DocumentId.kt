package dev.study.airag.domain.vo

import java.util.UUID

/**
 * 등록된 지식 문서를 수명 주기 전체에서 추적하는 식별자다.
 *
 * 재색인으로 문서 버전이 바뀌더라도 이 값은 유지된다.
 */
@JvmInline
value class DocumentId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        /** 새 지식 문서에 부여할 식별자를 생성한다. */
        fun newId(): DocumentId = DocumentId(UUID.randomUUID())

        /** UUID 형식이 아니면 변환에 실패한다. */
        fun from(value: String): DocumentId =
            try {
                DocumentId(UUID.fromString(value))
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("문서 ID는 올바른 UUID 형식이어야 합니다: $value", exception)
            }
    }
}
