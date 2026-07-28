package dev.study.airag.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.study.airag.application.graph.port.out.KnowledgeGraphIndexPort
import dev.study.airag.application.graph.port.out.KnowledgeGraphQueryPort
import dev.study.airag.domain.event.KnowledgeDocumentEvent
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.scheduling.annotation.Scheduled
import kotlin.test.assertTrue

class HexagonalArchitectureTests {
    private val classes =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.study.airag")

    @Test
    fun `production Kotlin files contain one top-level type matching the file name`() {
        val violations =
            classes
                .filter { javaClass ->
                    javaClass.enclosingClass.isEmpty &&
                        !javaClass.simpleName.endsWith("Kt") &&
                        javaClass.source
                            .flatMap { it.fileName }
                            .filter { it.endsWith(".kt") }
                            .isPresent
                }.mapNotNull { javaClass ->
                    val fileName = javaClass.source.flatMap { it.fileName }.orElse(null)
                    if (fileName == "${javaClass.simpleName}.kt") null else "$fileName: ${javaClass.name}"
                }

        assertTrue(
            violations.isEmpty(),
            "top-level 타입명과 Kotlin 파일명이 일치해야 합니다:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `owned application methods have at most two parameters`() {
        val violations =
            classes
                .filter { javaClass ->
                    javaClass.enclosingClass.isEmpty &&
                        javaClass.packageName.contains(".application.") &&
                        (
                            javaClass.packageName.contains(".service") ||
                                (
                                    javaClass.packageName.contains(".port.") &&
                                        !javaClass.packageName.contains(".dto")
                                )
                        )
                }.flatMap { it.methods }
                .filterNot { it.name.startsWith("copy") || it.name.contains('$') }
                .filter { it.rawParameterTypes.size > 2 }
                .map { "${it.fullName} (${it.rawParameterTypes.size} parameters)" }

        assertTrue(
            violations.isEmpty(),
            "Application Service/Port 메서드의 파라미터는 최대 2개여야 합니다:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `knowledge graph persistence separates index and query adapters`() {
        val violations =
            classes
                .filter {
                    it.packageName.contains(".adapter.out.persistence.postgres.graph.") &&
                        it.isAssignableTo(KnowledgeGraphIndexPort::class.java) &&
                        it.isAssignableTo(KnowledgeGraphQueryPort::class.java)
                }.map { it.name }

        assertTrue(
            violations.isEmpty(),
            "하나의 PostgreSQL Graph Adapter가 쓰기와 조회 Port를 함께 구현할 수 없습니다:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `knowledge application depends on graph ports instead of graph services`() {
        noClasses()
            .that()
            .resideInAPackage("..application.knowledge.service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application.graph.service..")
            .check(classes)
    }

    @Test
    fun `mappers do not call repositories or services`() {
        noClasses()
            .that()
            .resideInAPackage("..mapper..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..service..")
            .check(classes)
    }

    @Test
    fun `every adapter has an explicit inbound or outbound direction`() {
        classes()
            .that()
            .resideInAPackage("..adapter..")
            .should()
            .resideInAnyPackage("..adapter.in..", "..adapter.out..")
            .check(classes)
    }

    @Test
    fun `domain does not depend on frameworks or outer layers`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "org.apache.kafka..",
                "io.milvus..",
                "..application..",
                "..adapter..",
            ).check(classes)
    }

    @Test
    fun `domain event package contains only knowledge document domain events`() {
        classes()
            .that()
            .resideInAPackage("..domain.event..")
            .should()
            .beAssignableTo(KnowledgeDocumentEvent::class.java)
            .check(classes)
    }

    @Test
    fun `application does not depend on adapters`() {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .check(classes)
    }

    @Test
    fun `inbound adapters do not depend on outbound adapters`() {
        noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out..")
            .check(classes)
    }

    @Test
    fun `outbound adapters do not depend on inbound adapters`() {
        noClasses()
            .that()
            .resideInAPackage("..adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.in..")
            .check(classes)
    }

    @Test
    fun `PostgreSQL persistence uses JPA instead of direct JDBC access`() {
        noClasses()
            .that()
            .resideInAPackage("..adapter.out.persistence.postgres..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.jdbc..",
                "org.springframework.data.jdbc..",
                "org.springframework.data.relational..",
            ).check(classes)
    }

    @Test
    fun `PostgreSQL repositories are Spring Data JPA repositories`() {
        classes()
            .that()
            .resideInAPackage("..adapter.out.persistence.postgres..")
            .and()
            .haveSimpleNameEndingWith("Repository")
            .should()
            .beAssignableTo(JpaRepository::class.java)
            .check(classes)
    }

    @Test
    fun `inbound adapters call inbound ports instead of outbound ports`() {
        noClasses()
            .that()
            .resideInAPackage("..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..port.out..")
            .check(classes)
    }

    @Test
    fun `scheduled triggers are inbound adapters`() {
        methods()
            .that()
            .areAnnotatedWith(Scheduled::class.java)
            .should()
            .beDeclaredInClassesThat()
            .resideInAPackage("..adapter.in..")
            .check(classes)
    }
}
