package dev.study.airag.adapter.out.persistence.postgres

import dev.study.airag.adapter.out.persistence.postgres.processedmessage.PostgresDocumentIndexingCompletionAdapter
import dev.study.airag.adapter.out.persistence.postgres.processedmessage.ProcessedMessageRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostgresDocumentIndexingCompletionAdapterTests {
    @Test
    fun `claim without a transaction returns a Korean error message`() {
        val adapter =
            PostgresDocumentIndexingCompletionAdapter(
                Mockito.mock(ProcessedMessageRepository::class.java),
                "indexer",
            )

        val exception = assertFailsWith<IllegalStateException> { adapter.claim(UUID.randomUUID()) }

        assertEquals("문서 색인 처리 권한 확인은 활성 트랜잭션 안에서 수행해야 합니다.", exception.message)
    }
}
