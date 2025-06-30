package nexus.enumerator.intellij

import nexus.enumerator.intellij.project_model.DependencyInfo
import nexus.enumerator.intellij.project_model.DependencyType
import java.io.FileInputStream
import java.nio.file.Path
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.XMLEvent

data class MavenProjectInfo(
    val groupId: String?,
    val artifactId: String?,
    val version: String?,
    val dependencies: List<DependencyInfo>
)

class MavenPomParser {
    private val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
    }

    fun parse(pomFile: Path): MavenProjectInfo {
        var groupId: String? = null
        var artifactId: String? = null
        var version: String? = null
        val dependencies = mutableListOf<DependencyInfo>()

        var currentText: String? = null
        var inDependencies = false
        var currentDependencyGroupId: String? = null
        var currentDependencyArtifactId: String? = null
        var currentDependencyVersion: String? = null
        var currentDependencyScope: String? = null

        try {
            val reader: XMLEventReader = xmlInputFactory.createXMLEventReader(FileInputStream(pomFile.toFile()))

            while (reader.hasNext()) {
                val event: XMLEvent = reader.nextEvent()

                when {
                    event.isStartElement -> {
                        val startElement = event.asStartElement()
                        val elementName = startElement.name.localPart
                        currentText = null // Reset text for new element

                        when {
                            elementName == "groupId" && !inDependencies -> {}
                            elementName == "artifactId" && !inDependencies -> {}
                            elementName == "version" && !inDependencies -> {}
                            elementName == "dependencies" -> inDependencies = true
                            inDependencies && elementName == "dependency" -> {
                                currentDependencyGroupId = null
                                currentDependencyArtifactId = null
                                currentDependencyVersion = null
                                currentDependencyScope = null
                            }
                            inDependencies && elementName == "groupId" -> {}
                            inDependencies && elementName == "artifactId" -> {}
                            inDependencies && elementName == "version" -> {}
                            inDependencies && elementName == "scope" -> {}
                        }
                    }
                    event.isCharacters -> {
                        currentText = event.asCharacters().data.trim()
                    }
                    event.isEndElement -> {
                        val endElement = event.asEndElement()
                        val elementName = endElement.name.localPart

                        when {
                            elementName == "groupId" && !inDependencies -> groupId = currentText ?: groupId
                            elementName == "artifactId" && !inDependencies -> artifactId = currentText ?: artifactId
                            elementName == "version" && !inDependencies -> version = currentText ?: version
                            elementName == "dependencies" -> inDependencies = false
                            elementName == "dependency" && inDependencies -> {
                                if (currentDependencyArtifactId != null) { // GroupId can be inherited
                                    dependencies.add(
                                        DependencyInfo(
                                            name = "${currentDependencyGroupId ?: "unknown"}:${currentDependencyArtifactId}", // Combine for name
                                            version = currentDependencyVersion,
                                            scope = currentDependencyScope,
                                            type = DependencyType.LIBRARY // Maven deps are libraries
                                        )
                                    )
                                }
                            }
                            inDependencies && elementName == "groupId" -> currentDependencyGroupId = currentText
                            inDependencies && elementName == "artifactId" -> currentDependencyArtifactId = currentText
                            inDependencies && elementName == "version" -> currentDependencyVersion = currentText
                            inDependencies && elementName == "scope" -> currentDependencyScope = currentText
                        }
                        currentText = null // Reset text after processing end element
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            System.err.println("Error parsing pom.xml ($pomFile): ${e.message}")
            // Return whatever was parsed, or rethrow/handle more gracefully
        }

        // Handle cases where parent POM might define G/V for the main artifact
        // This basic parser doesn't handle parent POM inheritance for groupId/version of the project itself.
        // It also doesn't handle properties, dependencyManagement, etc.

        return MavenProjectInfo(groupId, artifactId, version, dependencies.distinct())
    }
}
