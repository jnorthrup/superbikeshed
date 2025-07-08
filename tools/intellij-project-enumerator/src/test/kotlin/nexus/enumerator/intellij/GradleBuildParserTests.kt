package nexus.enumerator.intellij

import nexus.enumerator.intellij.project_model.DependencyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class GradleBuildParserTests {

    internal val parser = GradleBuildParser()

    internal fun getResourcePath(fileName: String): Path {
        val resource = this::class.java.classLoader.getResource(fileName)
        assertNotNull(resource, "Test resource '$fileName' not found.")
        return Paths.get(resource.toURI())
    }

    @Test
    fun `parse sample_build_gradle correctly`() {
        val buildFile = getResourcePath("sample_build.gradle")
        val projectInfo = parser.parse(buildFile)

        assertEquals("com.example.gradle", projectInfo.group)
        assertEquals("1.0.SNAPSHOT", projectInfo.version)

        assertEquals(4, projectInfo.dependencies.size) // slf4j, junit, logback, guava, spring-boot-starter-web (platform is extra)

        val slf4j = projectInfo.dependencies.find { it.name == "org.slf4j:slf4j-api" }
        assertNotNull(slf4j)
        assertEquals("1.7.32", slf4j.version)
        assertEquals("COMPILE", slf4j.scope) // implementation maps to COMPILE
        assertEquals(DependencyType.LIBRARY, slf4j.type)

        val junit = projectInfo.dependencies.find { it.name == "junit:junit" }
        assertNotNull(junit)
        assertEquals("4.13.2", junit.version)
        assertEquals("TEST", junit.scope) // testImplementation maps to TEST

        val guava = projectInfo.dependencies.find { it.name == "com.google.guava:guava" }
        assertNotNull(guava)
        assertEquals("30.1-jre", guava.version)
        assertEquals("COMPILE", guava.scope)

        val springBootWeb = projectInfo.dependencies.find { it.name == "org.springframework.boot:spring-boot-starter-web" }
        assertNotNull(springBootWeb, "Spring Boot Starter Web dependency not found or parsed correctly")
        assertNull(springBootWeb.version, "Version should be null as it's managed by BOM")

        val springBootPlatform = projectInfo.dependencies.find { it.name == "org.springframework.boot:spring-boot-dependencies" && it.scope == "platform" }
        assertNotNull(springBootPlatform, "Spring Boot platform dependency not found or parsed correctly")
        assertEquals("2.7.0", springBootPlatform.version)

    }

    @Test
    fun `parse sample_build_gradle_kts correctly`() {
        val buildFile = getResourcePath("sample_build.gradle.kts")
        val projectInfo = parser.parse(buildFile)

        assertEquals("com.example.gradlekts", projectInfo.group)
        assertEquals("0.1.0", projectInfo.version)

        // stdlib, test, log4j, jackson, http4k-core, http4k-bom (platform)
        assertEquals(6, projectInfo.dependencies.size)

        val stdlib = projectInfo.dependencies.find { it.name == "org.jetbrains.kotlin:kotlin-stdlib-jdk8" }
        assertNotNull(stdlib)
        assertNull(stdlib.version) // Version not specified in this format
        assertEquals("COMPILE", stdlib.scope)

        val jackson = projectInfo.dependencies.find { it.name == "com.fasterxml.jackson.core:jackson-databind" }
        assertNotNull(jackson)
        assertEquals("2.13.0", jackson.version)
        assertEquals("COMPILE", jackson.scope)

        val http4kCore = projectInfo.dependencies.find { it.name == "org.http4k:http4k-core"}
        assertNotNull(http4kCore)
        assertNull(http4kCore.version) // Version from BOM

        val http4kBom = projectInfo.dependencies.find { it.name == "org.http4k:http4k-bom" && it.scope == "platform" }
        assertNotNull(http4kBom)
        assertEquals("4.25.0.0", http4kBom.version)
    }

    @Test
    fun `parse empty build file returns default values`() {
        val tempFile = kotlin.io.path.createTempFile(suffix = ".gradle")
        kotlin.io.path.writeText(tempFile, "// Empty build file")

        val projectInfo = parser.parse(tempFile)
        assertNull(projectInfo.group)
        assertNull(projectInfo.version)
        assertTrue(projectInfo.dependencies.isEmpty())

        kotlin.io.path.deleteIfExists(tempFile)
    }
}
