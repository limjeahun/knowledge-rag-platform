package dev.study.airag.adapter.out.ai.ollama

import dev.study.airag.application.port.out.ChunkKnowledgeDocumentPort
import dev.study.airag.domain.model.KnowledgeChunk
import dev.study.airag.domain.model.KnowledgeDocument
import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 설정된 크기와 개수 제한에 따라 원본 문서를 검색 가능한 근거 단위로 나눈다. */
@Component
class SpringAiDocumentChunkAdapter(
    @Value("\${app.knowledge.chunk-size}") chunkSize: Int,
    @Value("\${app.knowledge.min-chunk-size-chars}") minChunkSizeChars: Int,
    @Value("\${app.knowledge.max-chunks}") maxChunks: Int,
) : ChunkKnowledgeDocumentPort {
    private val splitter =
        TokenTextSplitter
            .builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(minChunkSizeChars)
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(maxChunks)
            .withKeepSeparator(true)
            .build()

    /**
     * 같은 문서 버전과 내용에는 같은 청크 식별자를 부여하고 원본 metadata를 보존한다.
     */
    override fun chunk(document: KnowledgeDocument): List<KnowledgeChunk> {
        val source = Document.builder().text(document.originalContent).build()
        return splitter.apply(listOf(source)).mapIndexed { index, chunk ->
            KnowledgeChunk(
                chunkId = "${document.id}-v${document.version}-c$index",
                documentId = document.id,
                documentVersion = document.version,
                chunkIndex = index,
                title = document.title,
                content = chunk.text.orEmpty(),
                metadata = document.metadata,
            )
        }
    }
}
