package dev.study.airag.application.service

import dev.study.airag.application.exception.InvalidKnowledgeGraphExtractionException
import dev.study.airag.application.port.out.dto.ExtractedGraphEntity
import dev.study.airag.application.port.out.dto.ExtractedGraphEvidence
import dev.study.airag.application.port.out.dto.ExtractedKnowledgeGraph
import dev.study.airag.application.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.port.out.dto.KnowledgeOntology
import dev.study.airag.application.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.port.out.dto.ProjectedGraphRelation
import dev.study.airag.domain.model.KnowledgeChunk
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.Locale

/**
 * LLM이 만든 후보를 신뢰 가능한 그래프 프로젝션으로 승격시키는 경계 검증기다.
 *
 * 모델 출력은 타입 이름, 관계 끝점, 인용문을 그럴듯하게 만들어 낼 수 있다. 따라서
 * 1) ontology에 선언된 타입인지, 2) 관계의 source/target 조합이 허용되는지,
 * 3) 인용문이 실제 청크에 존재하는지를 모두 확인한다. 하나라도 어기면 해당 문서 색인을
 * 실패시켜 잘못된 사실이 조용히 축적되지 않도록 한다.
 */
@Component
class KnowledgeGraphExtractionValidator {
    fun validateAndMerge(
        ontology: KnowledgeOntology,
        batches: List<KnowledgeGraphExtractionBatch>,
        policy: KnowledgeGraphProjectionPolicy,
    ): ValidatedKnowledgeGraph {
        validateOntology(ontology)
        val entities = linkedMapOf<KnowledgeGraphEntityKey, MutableProjectedEntity>()
        val relations = linkedMapOf<ProjectedRelationKey, MutableProjectedRelation>()

        batches.forEach { batch ->
            val validated = validateBatch(ontology, batch, policy.minimumConfidence)
            validated.entities.forEach { entity -> entities.merge(entity) }
            validated.relations.forEach { relation -> relations.merge(relation) }
        }

        if (entities.size > policy.maxEntitiesPerDocument) {
            invalid("문서에서 추출된 개체가 제한(${policy.maxEntitiesPerDocument})을 초과했습니다.")
        }
        if (relations.size > policy.maxRelationsPerDocument) {
            invalid("문서에서 추출된 관계가 제한(${policy.maxRelationsPerDocument})을 초과했습니다.")
        }
        return ValidatedKnowledgeGraph(
            entities = entities.values.map { it.toProjected() },
            relations = relations.values.map { it.toProjected() },
        )
    }

    private fun validateBatch(
        ontology: KnowledgeOntology,
        batch: KnowledgeGraphExtractionBatch,
        minimumConfidence: Double,
    ): ValidatedKnowledgeGraph {
        val chunksById = batch.chunks.associateBy { it.chunkId }
        val acceptedEntities =
            batch.extraction.entities
                .filter { requireConfidence(it.confidence, "개체 ${it.localKey}") >= minimumConfidence }
        val duplicateKeys =
            acceptedEntities
                .groupingBy { it.localKey.trim() }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (duplicateKeys.isNotEmpty()) invalid("한 추출 응답에서 개체 localKey가 중복되었습니다: $duplicateKeys")

        val entityByLocalKey =
            acceptedEntities.associate { entity ->
                val localKey = entity.localKey.trim()
                if (localKey.isEmpty()) invalid("개체 localKey는 비어 있을 수 없습니다.")
                localKey to validateEntity(ontology, entity, chunksById)
            }

        val relations =
            batch.extraction.relations
                .filter { requireConfidence(it.confidence, "관계 ${it.type}") >= minimumConfidence }
                .map { relation ->
                    val source =
                        entityByLocalKey[relation.sourceKey.trim()]
                            ?: invalid("관계 ${relation.type}의 sourceKey가 유효한 개체를 가리키지 않습니다.")
                    val target =
                        entityByLocalKey[relation.targetKey.trim()]
                            ?: invalid("관계 ${relation.type}의 targetKey가 유효한 개체를 가리키지 않습니다.")
                    if (source.key == target.key) invalid("자기 자신을 가리키는 관계 ${relation.type}는 저장하지 않습니다.")

                    val relationType =
                        ontology.relationTypesByCode[relation.type]
                            ?: invalid("온톨로지에 없는 관계 타입입니다: ${relation.type}")
                    if (source.key.type !in relationType.sourceTypes || target.key.type !in relationType.targetTypes) {
                        invalid(
                            "관계 ${relation.type}에 허용되지 않은 개체 타입 조합입니다: " +
                                "${source.key.type} -> ${target.key.type}",
                        )
                    }
                    ProjectedGraphRelation(
                        type = relation.type,
                        source = source.key,
                        target = target.key,
                        evidence = validateEvidence(relation.evidence, relation.confidence, chunksById),
                    )
                }
        return ValidatedKnowledgeGraph(entityByLocalKey.values.toList(), relations)
    }

    private fun validateEntity(
        ontology: KnowledgeOntology,
        entity: ExtractedGraphEntity,
        chunksById: Map<String, KnowledgeChunk>,
    ): ProjectedGraphEntity {
        if (entity.type !in ontology.entityTypesByCode) {
            invalid("온톨로지에 없는 개체 타입입니다: ${entity.type}")
        }
        val name = entity.name.trim()
        if (name.isEmpty()) invalid("그래프 개체 이름은 비어 있을 수 없습니다.")
        if (name.length > MAX_ENTITY_NAME_LENGTH) {
            invalid("그래프 개체 이름은 ${MAX_ENTITY_NAME_LENGTH}자를 초과할 수 없습니다.")
        }
        val key = KnowledgeGraphEntityKey(entity.type, normalizeName(name))
        return ProjectedGraphEntity(
            key = key,
            name = name,
            aliases =
                entity.aliases
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet() - name,
            evidence = validateEvidence(entity.evidence, entity.confidence, chunksById),
        )
    }

    private fun validateEvidence(
        evidence: List<ExtractedGraphEvidence>,
        confidence: Double,
        chunksById: Map<String, KnowledgeChunk>,
    ): List<KnowledgeGraphEvidence> {
        if (evidence.isEmpty()) invalid("개체와 관계에는 최소 한 개의 원문 근거가 필요합니다.")
        return evidence
            .map { candidate ->
                val chunk =
                    chunksById[candidate.chunkId]
                        ?: invalid("현재 추출 요청에 없는 chunkId가 근거로 반환되었습니다: ${candidate.chunkId}")
                val quote = candidate.quote.trim()
                if (quote.isEmpty() || !chunk.content.contains(quote)) {
                    invalid("근거 인용문이 실제 청크 본문에 존재하지 않습니다: ${candidate.chunkId}")
                }
                KnowledgeGraphEvidence(candidate.chunkId, quote, confidence)
            }.distinctBy { it.chunkId to it.quote }
    }

    private fun validateOntology(ontology: KnowledgeOntology) {
        if (ontology.version.isBlank()) invalid("온톨로지 버전은 비어 있을 수 없습니다.")
        if (ontology.entityTypes.isEmpty()) invalid("온톨로지에는 최소 한 개의 개체 타입이 필요합니다.")
        if (ontology.entityTypesByCode.size != ontology.entityTypes.size) invalid("온톨로지 개체 타입 코드가 중복되었습니다.")
        if (ontology.relationTypesByCode.size != ontology.relationTypes.size) {
            invalid("온톨로지 관계 타입 코드가 중복되었습니다.")
        }
        ontology.relationTypes.forEach { relation ->
            val unknown = (relation.sourceTypes + relation.targetTypes) - ontology.entityTypesByCode.keys
            if (unknown.isNotEmpty()) invalid("관계 ${relation.code}가 알 수 없는 개체 타입을 참조합니다: $unknown")
        }
    }

    private fun requireConfidence(
        confidence: Double,
        subject: String,
    ): Double {
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            invalid("$subject 신뢰도는 0.0 이상 1.0 이하이어야 합니다.")
        }
        return confidence
    }

    private fun MutableMap<KnowledgeGraphEntityKey, MutableProjectedEntity>.merge(entity: ProjectedGraphEntity) {
        val target = getOrPut(entity.key) { MutableProjectedEntity(entity.key, entity.name) }
        target.aliases += entity.aliases
        target.evidence.merge(entity.evidence)
    }

    private fun MutableMap<ProjectedRelationKey, MutableProjectedRelation>.merge(relation: ProjectedGraphRelation) {
        val key = ProjectedRelationKey(relation.type, relation.source, relation.target)
        val target = getOrPut(key) { MutableProjectedRelation(key) }
        target.evidence.merge(relation.evidence)
    }

    private fun MutableMap<Pair<String, String>, KnowledgeGraphEvidence>.merge(
        additions: List<KnowledgeGraphEvidence>,
    ) {
        additions.forEach { evidence ->
            val key = evidence.chunkId to evidence.quote
            val current = this[key]
            if (current == null || current.confidence < evidence.confidence) this[key] = evidence
        }
    }

    private fun normalizeName(name: String): String =
        Normalizer
            .normalize(name, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")

    private fun invalid(message: String): Nothing = throw InvalidKnowledgeGraphExtractionException(message)

    private data class MutableProjectedEntity(
        val key: KnowledgeGraphEntityKey,
        val name: String,
        val aliases: MutableSet<String> = linkedSetOf(),
        val evidence: MutableMap<Pair<String, String>, KnowledgeGraphEvidence> = linkedMapOf(),
    ) {
        fun toProjected() = ProjectedGraphEntity(key, name, aliases, evidence.values.toList())
    }

    private data class ProjectedRelationKey(
        val type: String,
        val source: KnowledgeGraphEntityKey,
        val target: KnowledgeGraphEntityKey,
    )

    private data class MutableProjectedRelation(
        val key: ProjectedRelationKey,
        val evidence: MutableMap<Pair<String, String>, KnowledgeGraphEvidence> = linkedMapOf(),
    ) {
        fun toProjected() =
            ProjectedGraphRelation(
                type = key.type,
                source = key.source,
                target = key.target,
                evidence = evidence.values.toList(),
            )
    }

    private companion object {
        const val MAX_ENTITY_NAME_LENGTH = 300
        val WHITESPACE = Regex("\\s+")
    }
}

data class KnowledgeGraphExtractionBatch(
    val chunks: List<KnowledgeChunk>,
    val extraction: ExtractedKnowledgeGraph,
)

data class ValidatedKnowledgeGraph(
    val entities: List<ProjectedGraphEntity>,
    val relations: List<ProjectedGraphRelation>,
)
