package dev.study.airag.adapter.`in`.web.graph.mapper

import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphEntityResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphEvidenceResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphNeighborhoodResponse
import dev.study.airag.adapter.`in`.web.graph.response.KnowledgeGraphRelationResponse
import dev.study.airag.application.dto.result.KnowledgeGraphEntityResult
import dev.study.airag.application.dto.result.KnowledgeGraphEvidenceResult
import dev.study.airag.application.dto.result.KnowledgeGraphNeighborhoodResult
import dev.study.airag.application.dto.result.KnowledgeGraphRelationResult

fun KnowledgeGraphEntityResult.toResponse() =
    KnowledgeGraphEntityResponse(
        entityId,
        ontologyVersion,
        type,
        name,
        aliases,
        evidence.map { it.toResponse() },
    )

fun KnowledgeGraphRelationResult.toResponse() =
    KnowledgeGraphRelationResponse(
        relationId,
        ontologyVersion,
        type,
        sourceEntityId,
        sourceName,
        targetEntityId,
        targetName,
        evidence.map { it.toResponse() },
    )

fun KnowledgeGraphEvidenceResult.toResponse() =
    KnowledgeGraphEvidenceResponse(documentId, documentVersion, chunkId, quote, confidence)

fun KnowledgeGraphNeighborhoodResult.toResponse() =
    KnowledgeGraphNeighborhoodResponse(
        center.toResponse(),
        entities.map { it.toResponse() },
        relations.map { it.toResponse() },
    )
