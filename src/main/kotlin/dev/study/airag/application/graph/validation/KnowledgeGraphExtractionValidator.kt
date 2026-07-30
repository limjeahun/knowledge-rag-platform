package dev.study.airag.application.graph.validation

import dev.study.airag.application.graph.exception.InvalidKnowledgeGraphExtractionException
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEntityKey
import dev.study.airag.application.graph.port.out.dto.KnowledgeGraphEvidence
import dev.study.airag.application.graph.port.out.dto.KnowledgeOntology
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphEntity
import dev.study.airag.application.graph.port.out.dto.ProjectedGraphRelation
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
    /**
     * 모든 LLM batch를 검증하고 문서 단위의 중복 없는 그래프로 병합한다.
     *
     * ontology 자체의 code 참조 무결성을 먼저 확인한 뒤 각 batch를 독립적으로 검증한다.
     * 개체는 타입과 정규화 이름, 관계는 타입과 방향성 끝점으로 합치며 같은 원문 근거가
     * 반복되면 confidence가 가장 높은 값을 보존한다. 병합을 마친 최종 개수에 문서 상한을 적용한다.
     *
     * @param request 배포 ontology, 청크별 추출 batch와 projection 정책
     * @return RDF 변환에 안전한 검증 완료 개체·관계
     * @throws InvalidKnowledgeGraphExtractionException ontology, 후보 또는 문서 상한 계약을 위반한 경우
     */
    fun validateAndMerge(request: KnowledgeGraphValidationRequest): ValidatedKnowledgeGraph {
        validateOntology(request.ontology)
        val entities = linkedMapOf<KnowledgeGraphEntityKey, MutableProjectedEntity>()
        val relations = linkedMapOf<ProjectedRelationKey, MutableProjectedRelation>()

        request.batches.forEach { batch ->
            val validated =
                validateBatch(
                    KnowledgeGraphBatchValidationRequest(
                        ontology = request.ontology,
                        batch = batch,
                        minimumConfidence = request.policy.minimumConfidence,
                    ),
                )
            validated.entities.forEach { entity -> entities.merge(entity) }
            validated.relations.forEach { relation -> relations.merge(relation) }
        }

        if (entities.size > request.policy.maxEntitiesPerDocument) {
            invalid("문서에서 추출된 개체가 제한(${request.policy.maxEntitiesPerDocument})을 초과했습니다.")
        }
        if (relations.size > request.policy.maxRelationsPerDocument) {
            invalid("문서에서 추출된 관계가 제한(${request.policy.maxRelationsPerDocument})을 초과했습니다.")
        }
        return ValidatedKnowledgeGraph(
            entities = entities.values.map { it.toProjected() },
            relations = relations.values.map { it.toProjected() },
        )
    }

    /**
     * 한 번의 LLM 응답을 같은 요청에 포함된 청크와 ontology 문법으로 검증한다.
     *
     * 기준 미만 confidence의 개체와 관계는 후보 단계에서 제외한다. 남은 개체 localKey는
     * 응답 안에서 유일해야 하며 관계의 sourceKey/targetKey는 같은 응답에서 수락된 개체만
     * 가리킬 수 있다. 관계 방향과 양 끝 타입은 OWL에서 투영한 허용 집합으로 확인한다.
     *
     * @param request ontology, 한 청크 batch와 최소 confidence
     * @return batch 내부에서 검증된 개체·관계
     * @throws InvalidKnowledgeGraphExtractionException local key, 관계 끝점, 타입 또는 근거가 유효하지 않은 경우
     */
    private fun validateBatch(request: KnowledgeGraphBatchValidationRequest): ValidatedKnowledgeGraph {
        val chunksById = request.batch.chunks.associateBy { it.chunkId }
        val acceptedEntities =
            request.batch.extraction.entities
                .filter { requireConfidence(it.confidence, "개체 ${it.localKey}") >= request.minimumConfidence }
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
                localKey to
                    validateEntity(
                        KnowledgeGraphEntityValidationRequest(
                            ontology = request.ontology,
                            entity = entity,
                            chunksById = chunksById,
                        ),
                    )
            }

        val relations =
            request.batch.extraction.relations
                .filter { requireConfidence(it.confidence, "관계 ${it.type}") >= request.minimumConfidence }
                .map { relation ->
                    val source =
                        entityByLocalKey[relation.sourceKey.trim()]
                            ?: invalid("관계 ${relation.type}의 sourceKey가 유효한 개체를 가리키지 않습니다.")
                    val target =
                        entityByLocalKey[relation.targetKey.trim()]
                            ?: invalid("관계 ${relation.type}의 targetKey가 유효한 개체를 가리키지 않습니다.")
                    if (source.key == target.key) invalid("자기 자신을 가리키는 관계 ${relation.type}는 저장하지 않습니다.")

                    val relationType =
                        request.ontology.relationTypesByCode[relation.type]
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
                        evidence =
                            validateEvidence(
                                KnowledgeGraphEvidenceValidationRequest(
                                    evidence = relation.evidence,
                                    confidence = relation.confidence,
                                    chunksById = chunksById,
                                ),
                            ),
                    )
                }
        return ValidatedKnowledgeGraph(entityByLocalKey.values.toList(), relations)
    }

    /**
     * 하나의 개체 후보를 안정적인 의미 키와 검증 완료 근거를 가진 projection 개체로 바꾼다.
     *
     * 타입 code가 ontology에 존재하는지 확인하고 이름의 공백과 최대 길이를 검증한다. 대표 이름은
     * 표시용 원문을 보존하지만 identity 비교에는 [normalizeName] 결과를 사용한다. 빈 alias와
     * 대표 이름과 동일한 alias는 제거한다.
     *
     * @param request ontology, 개체 후보와 이번 요청에서 허용된 청크
     * @return 정규화된 identity와 provenance를 가진 개체
     * @throws InvalidKnowledgeGraphExtractionException 타입, 이름 또는 evidence가 유효하지 않은 경우
     */
    private fun validateEntity(request: KnowledgeGraphEntityValidationRequest): ProjectedGraphEntity {
        if (request.entity.type !in request.ontology.entityTypesByCode) {
            invalid("온톨로지에 없는 개체 타입입니다: ${request.entity.type}")
        }
        val name = request.entity.name.trim()
        if (name.isEmpty()) invalid("그래프 개체 이름은 비어 있을 수 없습니다.")
        if (name.length > MAX_ENTITY_NAME_LENGTH) {
            invalid("그래프 개체 이름은 ${MAX_ENTITY_NAME_LENGTH}자를 초과할 수 없습니다.")
        }
        val key = KnowledgeGraphEntityKey(request.entity.type, normalizeName(name))
        return ProjectedGraphEntity(
            key = key,
            name = name,
            aliases =
                request.entity.aliases
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet() - name,
            evidence =
                validateEvidence(
                    KnowledgeGraphEvidenceValidationRequest(
                        evidence = request.entity.evidence,
                        confidence = request.entity.confidence,
                        chunksById = request.chunksById,
                    ),
                ),
        )
    }

    /**
     * 후보 evidence가 현재 batch의 실제 원문 청크에 존재하는 정확한 인용인지 검사한다.
     *
     * 모델이 다른 문서의 chunk ID나 생성한 문장을 근거로 제출하는 것을 차단한다. quote는
     * 양끝 공백을 제거한 뒤 청크 본문의 대소문자까지 정확한 연속 부분 문자열이어야 한다.
     * 동일 chunk·quote 조합은 한 번만 보존하고 후보의 검증된 confidence를 연결한다.
     *
     * @param request 후보 evidence, 소유 후보의 confidence와 허용 청크 map
     * @return 중복 없는 직접 원문 근거
     * @throws InvalidKnowledgeGraphExtractionException 근거가 없거나 chunk·quote가 원문과 일치하지 않는 경우
     */
    private fun validateEvidence(request: KnowledgeGraphEvidenceValidationRequest): List<KnowledgeGraphEvidence> {
        if (request.evidence.isEmpty()) invalid("개체와 관계에는 최소 한 개의 원문 근거가 필요합니다.")
        return request.evidence
            .map { candidate ->
                val chunk =
                    request.chunksById[candidate.chunkId]
                        ?: invalid("현재 추출 요청에 없는 chunkId가 근거로 반환되었습니다: ${candidate.chunkId}")
                val quote = candidate.quote.trim()
                if (quote.isEmpty() || !chunk.content.contains(quote)) {
                    invalid("근거 인용문이 실제 청크 본문에 존재하지 않습니다: ${candidate.chunkId}")
                }
                KnowledgeGraphEvidence(candidate.chunkId, quote, request.confidence)
            }.distinctBy { it.chunkId to it.quote }
    }

    /**
     * 외부 OWL Adapter가 제공한 Application ontology projection의 참조 무결성을 검사한다.
     *
     * version과 최소 한 개의 개체 타입을 요구하고 개체·관계 code 중복을 금지한다. 모든 관계의
     * source/target code가 실제 개체 타입을 가리키는지 확인하여 LLM 프롬프트와 후속 검증이
     * 서로 다른 문법을 사용하지 않게 한다.
     *
     * @param ontology 이번 문서 추출에 사용할 code 기반 의미 계약
     * @throws InvalidKnowledgeGraphExtractionException version, code 또는 관계 참조가 유효하지 않은 경우
     */
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

    /**
     * 모델 confidence가 유한한 폐구간 `0.0..1.0` 값인지 확인하고 그대로 반환한다.
     *
     * NaN은 범위 비교만으로 놓칠 수 있으므로 [Double.isFinite]를 별도로 검사한다.
     *
     * @param confidence 모델이 반환한 신뢰도
     * @param subject 오류 메시지에서 후보를 식별할 설명
     * @return 검증된 원래 confidence
     * @throws InvalidKnowledgeGraphExtractionException NaN, 무한대 또는 범위 밖 값인 경우
     */
    private fun requireConfidence(
        confidence: Double,
        subject: String,
    ): Double {
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            invalid("$subject 신뢰도는 0.0 이상 1.0 이하이어야 합니다.")
        }
        return confidence
    }

    /**
     * 같은 타입·정규화 이름의 개체를 문서 전체 accumulator에 병합한다.
     *
     * 최초 대표 이름은 유지하고 이후 batch의 별칭과 evidence만 합친다.
     */
    private fun MutableMap<KnowledgeGraphEntityKey, MutableProjectedEntity>.merge(entity: ProjectedGraphEntity) {
        val target = getOrPut(entity.key) { MutableProjectedEntity(entity.key, entity.name) }
        target.aliases += entity.aliases
        target.evidence.merge(entity.evidence)
    }

    /**
     * 같은 관계 타입·source·target의 방향성 관계를 문서 전체 accumulator에 병합한다.
     *
     * 반대 방향 관계는 source와 target이 달라 별도 항목으로 유지된다.
     */
    private fun MutableMap<ProjectedRelationKey, MutableProjectedRelation>.merge(relation: ProjectedGraphRelation) {
        val key = ProjectedRelationKey(relation.type, relation.source, relation.target)
        val target = getOrPut(key) { MutableProjectedRelation(key) }
        target.evidence.merge(relation.evidence)
    }

    /**
     * chunk ID와 quote가 같은 evidence를 합치고 가장 높은 confidence를 보존한다.
     *
     * @receiver `(chunkId, quote)`를 key로 사용하는 provenance accumulator
     * @param additions 새 batch 또는 동일 의미 개체·관계에서 발견한 근거
     */
    private fun MutableMap<Pair<String, String>, KnowledgeGraphEvidence>.merge(
        additions: List<KnowledgeGraphEvidence>,
    ) {
        additions.forEach { evidence ->
            val key = evidence.chunkId to evidence.quote
            val current = this[key]
            if (current == null || current.confidence < evidence.confidence) this[key] = evidence
        }
    }

    /**
     * 개체 병합과 결정적 RDF ID 생성에 사용할 언어 중립 이름 키를 만든다.
     *
     * Unicode NFKC로 호환 문자를 통일하고 양끝 공백, 대소문자와 연속 공백 차이를 제거한다.
     * 표시 이름 자체는 바꾸지 않고 identity 비교에만 이 값을 사용한다.
     *
     * @return 동일 의미 비교를 위한 정규화 문자열
     */
    private fun normalizeName(name: String): String =
        Normalizer
            .normalize(name, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")

    /**
     * 모든 추출 계약 위반을 호출자가 구분 가능한 단일 Application 예외로 변환한다.
     *
     * 반환 타입 [Nothing]으로 검증 분기에서 즉시 흐름이 종료됨을 컴파일러에 알린다.
     */
    private fun invalid(message: String): Nothing = throw InvalidKnowledgeGraphExtractionException(message)

    private data class MutableProjectedEntity(
        val key: KnowledgeGraphEntityKey,
        val name: String,
        val aliases: MutableSet<String> = linkedSetOf(),
        val evidence: MutableMap<Pair<String, String>, KnowledgeGraphEvidence> = linkedMapOf(),
    ) {
        /** 병합 완료된 별칭과 evidence snapshot을 불변 projection 개체로 고정한다. */
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
        /** 병합 완료된 방향성 관계 키와 evidence snapshot을 불변 projection 관계로 고정한다. */
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
