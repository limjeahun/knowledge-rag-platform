package dev.study.airag.application.port.out

import dev.study.airag.application.model.publication.DocumentIndexingPublication

fun interface PublishDocumentIndexingPort {
    /** 색인 요청을 비동기 처리 채널에 전달하며 성공을 확인하지 못하면 실패로 처리한다. */
    fun publish(publication: DocumentIndexingPublication)
}
