package k2script.util

import org.w3c.dom.Element
import java.nio.file.Path
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object MavenToGradleConverter {
    fun generateGradleFromPom(surrogateDir: Path) {
        val pomFile = surrogateDir.resolve("pom.xml").toFile()
        if (!pomFile.exists()) throw IllegalArgumentException("pom.xml not found in $surrogateDir")

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
        doc.documentElement.normalize()

        // Extract properties
        val properties = mutableMapOf<String, String>()
        doc.getElementsByTagName("properties").item(0)?.let { node ->
            if (node is Element) {
                val children = node.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child is Element) {
                        properties[child.tagName] = child.textContent
                    }
                }
            }
        }

        // Extract dependencies
        val dependencies = mutableListOf<Triple<String, String, String>>()
        val depsList = doc.getElementsByTagName("dependency")
        for (i in 0 until depsList.length) {
            val dep = depsList.item(i) as? Element ?: continue
            val groupId = dep.getElementsByTagName("groupId").item(0)?.textContent ?: continue
            val artifactId = dep.getElementsByTagName("artifactId").item(0)?.textContent ?: continue
            val version = dep.getElementsByTagName("version").item(0)?.textContent ?: continue
            dependencies.add(Triple(groupId, artifactId, version))
        }

        // Extract repositories
        val repositories = mutableListOf<String>()
        val repoList = doc.getElementsByTagName("repository")
        for (i in 0 until repoList.length) {
            val repo = repoList.item(i) as? Element ?: continue
            val url = repo.getElementsByTagName("url").item(0)?.textContent ?: continue
            repositories.add(url)
        }

        // Project name
        val projectName = doc.getElementsByTagName("artifactId").item(0)?.textContent ?: "k2script-surrogate"

        // Write build.gradle.kts
        val gradleFile = surrogateDir.resolve("build.gradle.kts").toFile()
        gradleFile.writeText(buildGradleKts(projectName, properties, dependencies, repositories))

        // Write settings.gradle.kts
        val settingsFile = surrogateDir.resolve("settings.gradle.kts").toFile()
        settingsFile.writeText("rootProject.name = \"$projectName\"")
    }

    private fun buildGradleKts(
        projectName: String,
        properties: Map<String, String>,
        dependencies: List<Triple<String, String, String>>,
        repositories: List<String>
    ): String {
        val kotlinVersion = properties["kotlin.version"] ?: "1.9.22"
        val serializationVersion = properties["serialization.version"] ?: "1.6.0"
        return """
            plugins {
                kotlin("jvm") version \"$kotlinVersion\"
                kotlin("plugin.serialization") version \"$kotlinVersion\"
                application
            }

            repositories {
                mavenCentral()
                ${repositories.joinToString("\n                ") { "maven { url = uri(\"$it\") }" }}
            }

            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                ${dependencies.joinToString("\n                ") { (g, a, v) -> "implementation(\"$g:$a:$v\")" }}
            }

            application {
                mainClass.set(\"${projectName}Kt\")
            }

            kotlin {
                jvmToolchain(17)
            }
        """.trimIndent()
    }
} 