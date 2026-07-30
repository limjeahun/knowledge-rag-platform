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
    /**
     * 새 원격 connection에서 READ transaction을 열어 블록을 실행하고 항상 종료한다.
     *
     * 읽기에는 commit이 필요하지 않다. 블록이 예외를 던져도 `end()`와 connection `close()`가
     * 차례로 실행되며 원래 예외는 그대로 호출자에게 전달된다.
     *
     * @param block connection이 유효한 동안 즉시 결과를 계산하는 읽기 작업
     * @return 읽기 transaction 안에서 계산된 결과
     */
    override fun <T> read(block: (RDFConnection) -> T): T =
        connect().use { connection ->
            connection.begin(ReadWrite.READ)
            try {
                block(connection)
            } finally {
                connection.end()
            }
        }

    /**
     * 새 원격 connection에서 WRITE transaction을 열어 전체 블록을 원자적으로 실행한다.
     *
     * 정상 반환 시 결과를 보존하면서 commit한다. 예외가 발생하면 abort한 뒤 같은 예외를
     * 다시 던지고, 마지막에 transaction과 connection을 닫는다.
     *
     * @param block 하나의 Fuseki transaction으로 묶을 dataset 변경
     * @return 성공적으로 commit된 블록 결과
     */
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

    /**
     * 설정된 dataset base URL을 대상으로 하는 Jena 원격 connection을 생성한다.
     *
     * 끝의 `/`를 제거해 Jena가 query, update, graph-store endpoint를 일관되게 파생하도록 한다.
     * connection의 소유권은 호출한 [read] 또는 [write] 메서드에 있다.
     *
     * @return 아직 transaction을 시작하지 않은 새 [RDFConnection]
     */
    private fun connect(): RDFConnection =
        RDFConnectionRemote
            .newBuilder()
            .destination(properties.fusekiDatasetUrl.removeSuffix("/"))
            .build()
}
