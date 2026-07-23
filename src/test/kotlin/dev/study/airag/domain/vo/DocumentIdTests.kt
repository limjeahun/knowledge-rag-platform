package dev.study.airag.domain.vo

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentIdTests {
    @Test
    fun `invalid UUID returns a Korean validation message`() {
        val exception = assertFailsWith<IllegalArgumentException> { DocumentId.from("invalid-id") }

        assertEquals("문서 ID는 올바른 UUID 형식이어야 합니다: invalid-id", exception.message)
    }
}
