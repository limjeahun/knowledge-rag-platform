package dev.study.airag.adapter.`in`.web.knowledge.mapper

import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeAnswerGraphFactResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeAnswerResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeDocumentResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.KnowledgeSearchHitResponse
import dev.study.airag.adapter.`in`.web.knowledge.response.RegisteredKnowledgeDocumentResponse
import dev.study.airag.application.knowledge.dto.result.KnowledgeAnswerResult
import dev.study.airag.application.knowledge.dto.result.KnowledgeDocumentResult
import dev.study.airag.application.knowledge.dto.result.KnowledgeSearchHit
import dev.study.airag.application.knowledge.dto.result.RegisteredKnowledgeDocumentResult

fun RegisteredKnowledgeDocumentResult.toResponse() = RegisteredKnowledgeDocumentResponse(documentId, status.name)

fun KnowledgeDocumentResult.toResponse() =
    KnowledgeDocumentResponse(
        documentId,
        title,
        version,
        status.name,
        failureReason,
        registeredAt,
        indexedAt,
    )

fun KnowledgeSearchHit.toResponse() =
    KnowledgeSearchHitResponse(
        chunkId,
        documentId,
        documentVersion,
        chunkIndex,
        title,
        content,
        score,
        metadata,
    )

fun KnowledgeAnswerResult.toResponse() =
    KnowledgeAnswerResponse(
        question = question,
        answer = answer,
        sources = sources.map { it.toResponse() },
        graphFacts =
            graphFacts.map {
                KnowledgeAnswerGraphFactResponse(
                    relationId = it.relationId,
                    ontologyVersion = it.ontologyVersion,
                    assertionKind = it.assertionKind.name,
                    type = it.type,
                    sourceEntityId = it.sourceEntityId,
                    sourceName = it.sourceName,
                    targetEntityId = it.targetEntityId,
                    targetName = it.targetName,
                )
            },
    )
