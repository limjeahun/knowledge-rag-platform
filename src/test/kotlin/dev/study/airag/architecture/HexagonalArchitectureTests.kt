package dev.study.airag.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import dev.study.airag.domain.event.KnowledgeDocumentEvent
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.scheduling.annotation.Scheduled

class HexagonalArchitectureTests {
    private val classes = ClassFileImporter().importPackages("dev.study.airag")

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
            .resideInAPackage("..application.port.out..")
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
