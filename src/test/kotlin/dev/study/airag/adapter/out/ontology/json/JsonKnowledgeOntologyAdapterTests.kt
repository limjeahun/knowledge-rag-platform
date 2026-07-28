package dev.study.airag.adapter.out.ontology.json

import dev.study.airag.config.KnowledgeGraphProperties
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JsonKnowledgeOntologyAdapterTests {
    @Test
    fun `loads the versioned ontology resource once as typed application data`() {
        val adapter =
            JsonKnowledgeOntologyAdapter(
                DefaultResourceLoader(),
                JsonMapper
                    .builder()
                    .addModule(KotlinModule.Builder().build())
                    .build(),
                KnowledgeGraphProperties(),
            )

        val ontology = adapter.load()

        assertEquals("knowledge-ontology-v1", ontology.version)
        assertTrue("TECHNOLOGY" in ontology.entityTypesByCode)
        assertEquals(setOf("DATA_STORE"), ontology.relationTypesByCode.getValue("STORES_IN").targetTypes)
        assertSame(ontology, adapter.load())
    }
}
