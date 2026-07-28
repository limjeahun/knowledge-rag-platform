package dev.study.airag.application.service

/**
 * 그래프 추출 비용과 신뢰 경계를 배포 설정으로 전달하는 기술 중립 정책이다.
 *
 * enabled가 true이면 그래프 생성도 문서 색인 완료 조건이다. 이때 추출 실패를 무시하고
 * Milvus만 성공 처리하면 같은 INDEXED 상태가 서로 다른 의미를 갖게 되므로, 전체 색인을
 * FAILED로 두어 재시도하게 한다.
 */
data class KnowledgeGraphProjectionPolicy(
    val enabled: Boolean,
    val chunksPerRequest: Int,
    val minimumConfidence: Double,
    val maxEntitiesPerDocument: Int,
    val maxRelationsPerDocument: Int,
) {
    init {
        require(chunksPerRequest > 0) { "그래프 추출 요청당 청크 수는 0보다 커야 합니다." }
        require(minimumConfidence in 0.0..1.0) { "그래프 최소 신뢰도는 0.0 이상 1.0 이하이어야 합니다." }
        require(maxEntitiesPerDocument > 0) { "문서당 그래프 개체 제한은 0보다 커야 합니다." }
        require(maxRelationsPerDocument > 0) { "문서당 그래프 관계 제한은 0보다 커야 합니다." }
    }
}
