package dev.study.airag.adapter.out.graph.rdf

import dev.study.airag.config.graph.KnowledgeGraphProperties
import org.apache.jena.query.ReadWrite
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.rdfconnection.RDFConnectionRemote
import org.springframework.stereotype.Component

/**
 * Jena RDFConnection으로 원격 Fuseki dataset transaction을 실행한다.
 *
 * 매 호출은 독립 connection과 명시적인 READ/WRITE transaction을 사용한다. 쓰기 블록은
 * 성공한 경우에만 commit하며, 예외가 발생하면 abort한 뒤 원래 예외를 전파한다.
 */
@Component
internal class FusekiRdfDatasetGateway(
    private val properties: KnowledgeGraphProperties,
) : RdfDatasetGateway {
    override fun <T> read(block: (RDFConnection) -> T): T =
        connect().use { connection ->
            connection.begin(ReadWrite.READ)
            try {
                block(connection)
            } finally {
                connection.end()
            }
        }

    override fun <T> write(block: (RDFConnection) -> T): T =
        connect().use { connection ->
            connection.begin(ReadWrite.WRITE)
            try {
                block(connection).also { connection.commit() }
            } catch (exception: Exception) {
                connection.abort()
                throw exception
            } finally {
                connection.end()
            }
        }

    private fun connect(): RDFConnection =
        RDFConnectionRemote
            .newBuilder()
            .destination(properties.fusekiDatasetUrl.removeSuffix("/"))
            .build()
}
