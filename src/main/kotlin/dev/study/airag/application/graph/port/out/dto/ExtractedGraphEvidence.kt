package dev.study.airag.application.graph.port.out.dto

/**
 * 추출된 사실이 어느 청크의 어떤 문장에 근거하는지 나타내는 출처다.
 *
 * quote는 검증 단계에서 실제 청크 본문에 존재하는지 확인한다. 이 검증을 통과하지 못한
 * 모델의 설명이나 추론은 그래프에 저장하지 않는다.
 */
data class ExtractedGraphEvidence(
    val chunkId: String,
    val quote: String,
)
