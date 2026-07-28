package dev.study.airag.application.graph.port.out.dto

/** 자연어에서 식별할 개체 분류와 모델에게 제공할 의미 설명이다. */
data class OntologyEntityType(
    val code: String,
    val description: String,
)
