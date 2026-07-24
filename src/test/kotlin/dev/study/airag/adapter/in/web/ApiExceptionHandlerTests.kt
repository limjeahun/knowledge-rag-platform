package dev.study.airag.adapter.`in`.web

import dev.study.airag.application.dto.command.DeleteKnowledgeDocumentCommand
import dev.study.airag.application.dto.command.RetryKnowledgeDocumentIndexingCommand
import dev.study.airag.application.dto.query.AnswerKnowledgeQuestionQuery
import dev.study.airag.application.exception.KnowledgeAnswerGenerationException
import dev.study.airag.application.exception.KnowledgeAnswerGenerationFailure
import dev.study.airag.application.exception.KnowledgeDocumentNotFoundException
import dev.study.airag.application.port.`in`.AnswerKnowledgeQuestionUseCase
import dev.study.airag.application.port.`in`.DeleteKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.GetKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RegisterKnowledgeDocumentUseCase
import dev.study.airag.application.port.`in`.RetryKnowledgeDocumentIndexingUseCase
import dev.study.airag.application.port.`in`.SearchKnowledgeUseCase
import dev.study.airag.domain.exception.InvalidDocumentStateTransitionException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@WebMvcTest(KnowledgeController::class)
class ApiExceptionHandlerTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var registerUseCase: RegisterKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var getUseCase: GetKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var retryUseCase: RetryKnowledgeDocumentIndexingUseCase

    @MockitoBean
    private lateinit var deleteUseCase: DeleteKnowledgeDocumentUseCase

    @MockitoBean
    private lateinit var searchUseCase: SearchKnowledgeUseCase

    @MockitoBean
    private lateinit var answerUseCase: AnswerKnowledgeQuestionUseCase

    @Test
    fun `missing knowledge document returns 404 error response`() {
        doThrow(KnowledgeDocumentNotFoundException("missing-document"))
            .`when`(getUseCase)
            .get("missing-document")

        mockMvc
            .get("/api/documents/missing-document")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error") { value("지식 문서를 찾을 수 없습니다: missing-document") }
            }
    }

    @Test
    fun `invalid document state returns 409 error response`() {
        doThrow(InvalidDocumentStateTransitionException("FAILED 상태의 문서만 재시도할 수 있습니다."))
            .`when`(retryUseCase)
            .retry(
                any(RetryKnowledgeDocumentIndexingCommand::class.java)
                    ?: RetryKnowledgeDocumentIndexingCommand(""),
            )

        mockMvc
            .post("/api/documents/pending-document/retry")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("FAILED 상태의 문서만 재시도할 수 있습니다.") }
            }
    }

    @Test
    fun `invalid application argument returns 400 error response`() {
        doThrow(IllegalArgumentException("문서 ID 형식이 올바르지 않습니다."))
            .`when`(deleteUseCase)
            .delete(
                any(DeleteKnowledgeDocumentCommand::class.java)
                    ?: DeleteKnowledgeDocumentCommand(""),
            )

        mockMvc
            .delete("/api/documents/invalid-id")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("문서 ID 형식이 올바르지 않습니다.") }
            }
    }

    @Test
    fun `request body validation failure returns field errors`() {
        mockMvc
            .post("/api/documents") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":" ","content":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") {
                    value("content: 본문은 비어 있을 수 없습니다.; title: 제목은 비어 있을 수 없습니다.")
                }
            }
    }

    @Test
    fun `chat request validation failure returns Korean field errors`() {
        mockMvc
            .post("/api/chat") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":" ","topK":0}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") {
                    value("question: 질문은 비어 있을 수 없습니다.; topK: topK는 1 이상이어야 합니다.")
                }
            }
    }

    @Test
    fun `truncated AI answer returns safe 502 error response`() {
        doThrow(KnowledgeAnswerGenerationException(KnowledgeAnswerGenerationFailure.OUTPUT_TRUNCATED))
            .`when`(answerUseCase)
            .answer(
                any(AnswerKnowledgeQuestionQuery::class.java)
                    ?: AnswerKnowledgeQuestionQuery(""),
            )

        mockMvc
            .post("/api/chat") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"React 입문 순서를 알려줘","topK":5,"similarityThreshold":0.5}"""
            }.andExpect {
                status { isBadGateway() }
                jsonPath("$.errorCode") { value("AI_ANSWER_TRUNCATED") }
                jsonPath("$.error") {
                    value("AI 모델이 응답 길이 한도 내에서 답변 생성을 완료하지 못했습니다.")
                }
            }
    }

    @Test
    fun `malformed JSON returns 400 error response`() {
        mockMvc
            .post("/api/chat") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("JSON 요청 본문 형식이 올바르지 않습니다.") }
            }
    }

    @Test
    fun `missing request parameter returns 400 error response`() {
        mockMvc
            .get("/api/search")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("필수 요청 파라미터가 누락되었습니다: query") }
            }
    }

    @Test
    fun `request parameter type mismatch returns 400 error response`() {
        mockMvc
            .get("/api/search") {
                param("query", "RAG")
                param("topK", "many")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("요청 파라미터 'topK'의 값은 int 형식이어야 합니다.") }
            }
    }

    @Test
    fun `unknown API path returns 404 error response`() {
        mockMvc
            .get("/api/unknown")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error") { value("요청한 리소스를 찾을 수 없습니다.") }
            }
    }

    @Test
    fun `unsupported HTTP method returns 405 error response`() {
        mockMvc
            .patch("/api/search")
            .andExpect {
                status { isMethodNotAllowed() }
                jsonPath("$.error") { value("지원하지 않는 HTTP 메서드입니다: PATCH") }
            }
    }

    @Test
    fun `unsupported content type returns 415 error response`() {
        mockMvc
            .post("/api/chat") {
                contentType = MediaType.TEXT_PLAIN
                content = "question"
            }.andExpect {
                status { isUnsupportedMediaType() }
                jsonPath("$.error") { value("지원하지 않는 Content-Type입니다: text/plain") }
            }
    }

    @Test
    fun `unexpected server failure returns safe 500 error response`() {
        doThrow(RuntimeException("database password must not leak"))
            .`when`(getUseCase)
            .get("server-error")

        mockMvc
            .get("/api/documents/server-error")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.error") { value("서버 내부 오류가 발생했습니다.") }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database password"))) }
            }
    }
}
