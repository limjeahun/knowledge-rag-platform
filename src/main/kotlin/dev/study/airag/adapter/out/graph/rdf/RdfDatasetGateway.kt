package dev.study.airag.adapter.out.graph.rdf

import org.apache.jena.rdfconnection.RDFConnection

/**
 * RDF dataset의 transaction 경계를 감추는 내부 Gateway다.
 *
 * 호출자는 connection의 시작·commit·abort·종료를 직접 다루지 않고 읽기 또는 쓰기 블록만
 * 제공한다. 구현체는 블록이 반환되기 전에 connection을 닫지 않아야 하며 쓰기 예외를 원인
 * 그대로 다시 던져 상위 색인 실패 흐름이 재시도할 수 있게 해야 한다.
 */
interface RdfDatasetGateway {
    /** 일관된 읽기 transaction 안에서 값을 계산한다. */
    fun <T> read(block: (RDFConnection) -> T): T

    /** 원자적 쓰기 transaction을 commit하고 실패 시 abort한다. */
    fun <T> write(block: (RDFConnection) -> T): T
}
