package dev.study.airag.adapter.`in`.web.ocr.exception

class UnsupportedOcrImageTypeException(
    contentType: String?,
) : RuntimeException("지원하지 않는 OCR 이미지 형식입니다: ${contentType ?: "알 수 없음"}")
