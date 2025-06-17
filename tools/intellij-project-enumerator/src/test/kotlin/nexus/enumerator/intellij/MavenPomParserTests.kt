package nexus.enumerator.intellij

import nexus.enumerator.intellij.project_model.DependencyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class MavenPomParserTests {

    private val parser = MavenPomParser()

    private fun getResourcePath(fileName: String): Path {
        val resource = this::class.java.classLoader.getResource(fileName)
        assertNotNull(resource, "Test resource '$fileName' not found.")
        return Paths.get(resource.toURI())
    }

    @Test
    fun `parse sample_pom_xml correctly`() {
        val pomFile = getResourcePath("sample_pom.xml")
        val projectInfo = parser.parse(pomFile)

        assertEquals("com.example.maven", projectInfo.groupId)
        assertEquals("maven-project", projectInfo.artifactId)
        assertEquals("1.0.0", projectInfo.version)

        assertEquals(3, projectInfo.dependencies.size)

        val junitDep = projectInfo.dependencies.find { it.name == "org.junit.jupiter:junit-jupiter-api" }
        assertNotNull(junitDep)
        assertEquals("5.8.2", junitDep.version)
        assertEquals("test", junitDep.scope)
        assertEquals(DependencyType.LIBRARY, junitDep.type)

        val gsonDep = projectInfo.dependencies.find { it.name == "com.google.code.gson:gson" }
        assertNotNull(gsonDep)
        assertEquals("2.9.0", gsonDep.version)
        assertNull(gsonDep.scope) // Default scope is compile, which might be null or "compile" depending on exact IML conversion
        assertEquals(DependencyType.LIBRARY, gsonDep.type)

        val commonsLangDep = projectInfo.dependencies.find { it.name == "org.apache.commons:commons-lang3" }
        assertNotNull(commonsLangDep)
        assertEquals("3.12.0", commonsLangDep.version)
        assertEquals("provided", commonsLangDep.scope)
        assertEquals(DependencyType.LIBRARY, commonsLangDep.type)
    }

    @Test
    fun `parse empty pom returns default values`() {
        val emptyPomContent = """
            <project>
                <modelVersion>4.0.0</modelVersion>
            </project>
        """.trimIndent()
        val tempFile = kotlin.io.path.createTempFile(suffix = ".xml")
        kotlin.io.path.writeText(tempFile, emptyPomContent)

        val projectInfo = parser.parse(tempFile)
        assertNull(projectInfo.groupId)
        assertNull(projectInfo.artifactId)
        assertNull(projectInfo.version)
        assertTrue(projectInfo.dependencies.isEmpty())

        kotlin.io.path.deleteIfExists(tempFile)
    }

    @Test
    fun `parse pom with missing dependency details`() {
        val pomContent = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <dependencies>
                    <dependency>
                        <artifactId>only-artifact</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """.trimIndent()
        val tempFile = kotlin.io.path.createTempFile(suffix = ".xml")
        kotlin.io.path.writeText(tempFile, pomContent)

        val projectInfo = parser.parse(tempFile)
        assertEquals(1, projectInfo.dependencies.size)
        val dep = projectInfo.dependencies.first()
        assertEquals("unknown:only-artifact", dep.name) // groupId defaults to "unknown"
        assertNull(dep.version)
        assertNull(dep.scope)

        kotlin.io.path.deleteIfExists(tempFile)
    }
}
