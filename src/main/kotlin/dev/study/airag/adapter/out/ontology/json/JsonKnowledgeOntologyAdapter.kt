package dev.study.airag.adapter.out.ontology.json

import dev.study.airag.application.port.out.KnowledgeOntologyPort
import dev.study.airag.application.port.out.dto.KnowledgeOntology
import dev.study.airag.application.port.out.dto.OntologyEntityType
import dev.study.airag.application.port.out.dto.OntologyRelationType
import dev.study.airag.config.KnowledgeGraphProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 버전형 JSON 파일을 애플리케이션의 ontology 계약으로 읽는다.
 *
 * JSON 모양과 Jackson은 이 adapter 안에 머문다. Application은 파일 경로나 JSON field를
 * 알지 않고, 타입이 있는 [KnowledgeOntology]만 사용한다.
 */
@Component
class JsonKnowledgeOntologyAdapter(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val properties: KnowledgeGraphProperties,
) : KnowledgeOntologyPort {
    private val ontology: KnowledgeOntology by lazy(::readOntology)

    /** 같은 애플리케이션 실행 중에는 동일 버전을 사용하여 한 색인 안에서 문법이 바뀌지 않게 한다. */
    override fun load(): KnowledgeOntology = ontology

    private fun readOntology(): KnowledgeOntology {
        val resource = resourceLoader.getResource(properties.ontologyLocation)
        require(resource.exists()) { "지식 그래프 온톨로지 파일을 찾을 수 없습니다: ${properties.ontologyLocation}" }
        val document = resource.inputStream.use { objectMapper.readValue(it, OntologyDocument::class.java) }
        return KnowledgeOntology(
            version = document.version,
            entityTypes = document.entityTypes.map { OntologyEntityType(it.code, it.description) },
            relationTypes =
                document.relationTypes.map {
                    OntologyRelationType(
                        code = it.code,
                        description = it.description,
                        sourceTypes = it.sourceTypes.toSet(),
                        targetTypes = it.targetTypes.toSet(),
                    )
                },
        )
    }

    private data class OntologyDocument(
        val version: String,
        val entityTypes: List<EntityTypeDocument>,
        val relationTypes: List<RelationTypeDocument>,
    )

    private data class EntityTypeDocument(
        val code: String,
        val description: String,
    )

    private data class RelationTypeDocument(
        val code: String,
        val description: String,
        val sourceTypes: List<String>,
        val targetTypes: List<String>,
    )
}
