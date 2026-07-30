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
    /**
     * 일관된 읽기 transaction 안에서 값을 계산한다.
     *
     * [block] 밖으로 [RDFConnection]을 보관하거나 지연 결과를 반환해서는 안 된다. 구현체가
     * 메서드 반환 직후 transaction과 connection을 종료하기 때문이다.
     *
     * @param block 열린 READ transaction 안에서 즉시 실행할 계산
     * @return connection이 닫히기 전에 완전히 계산된 값
     */
    fun <T> read(block: (RDFConnection) -> T): T

    /**
     * 원자적 쓰기 transaction을 commit하고 실패 시 abort한다.
     *
     * [block]이 정상 반환한 경우에만 commit해야 하며 어떤 예외도 성공으로 바꾸지 않는다.
     * 이 계약 덕분에 여러 named graph와 catalog pointer가 부분 갱신되지 않는다.
     *
     * @param block 열린 WRITE transaction 안에서 수행할 전체 dataset 변경
     * @return commit 전에 블록이 계산한 값
     */
    fun <T> write(block: (RDFConnection) -> T): T
}
