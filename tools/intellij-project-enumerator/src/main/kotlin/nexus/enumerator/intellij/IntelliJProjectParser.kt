// TODO: Resolve model duplication. Import from shared module once available.
package nexus.enumerator.intellij

import nexus.enumerator.intellij.project_model.*
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

class IntelliJProjectParser {

    internal val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
    }
    internal val mavenParser = MavenPomParser()
    internal val gradleParser = GradleBuildParser()

    fun parseProject(projectRootDirPath: String): IntelliJProjectDetails {
        val projectRoot = Paths.get(projectRootDirPath).toAbsolutePath()
        if (!projectRoot.toFile().isDirectory) {
            throw IllegalArgumentException("Project root path does not exist or is not a directory: $projectRootDirPath")
        }

        val ideaDir = projectRoot.resolve(".idea")
        if (!ideaDir.toFile().isDirectory) {
            throw IllegalArgumentException("Missing .idea directory in project root: $ideaDir")
        }

        val projectName = parseProjectName(ideaDir, projectRoot)
        val (projectSdkName, projectJdkVersion) = parseProjectSdk(ideaDir)
        var projectGroupId: String? = null
        var projectVersion: String? = null

        val imlFilePaths = parseModulesXml(ideaDir, projectRoot)
        val parsedModules = mutableListOf<ModuleDetails>()
        for (imlPath in imlFilePaths) {
            try {
                val moduleDetails = parseImlFile(imlPath, projectRoot)
                parsedModules.add(moduleDetails)
            } catch (e: Exception) {
                System.err.println("Error parsing IML file ${imlPath}: ${e.message}")
            }
        }

        val projectBuildSystemInfo = locateAndParseBuildSystem(projectRoot, projectRoot, isModuleScan = false) { info ->
            when (info) {
                is MavenProjectInfo -> {
                    projectGroupId = info.groupId
                    projectVersion = info.version
                }
                is GradleProjectInfo -> {
                    projectGroupId = info.group
                    projectVersion = info.version
                }
            }
        }

        val finalModules = parsedModules.map { module ->
            val moduleDir = Paths.get(module.imlPath).parent
            var moduleGroupId = module.moduleGroupId
            var moduleVersion = module.moduleVersion
            var finalDeps = module.dependencies.toMutableList()

            val moduleBuildSystemInfo = locateAndParseBuildSystem(moduleDir, projectRoot, isModuleScan = true) { info ->
                 when (info) {
                    is MavenProjectInfo -> {
                        moduleGroupId = info.groupId ?: moduleGroupId
                        moduleVersion = info.version ?: moduleVersion
                        finalDeps = mergeDependencies(finalDeps, info.dependencies)
                    }
                    is GradleProjectInfo -> {
                        moduleGroupId = info.group ?: moduleGroupId
                        moduleVersion = info.version ?: moduleVersion
                        finalDeps = mergeDependencies(finalDeps, info.dependencies)
                    }
                }
            }
            // If module has no specific build file, it might inherit project's build system context for dependencies.
            // However, dependency merging usually happens if the module itself has a build file.
            // If project has build info and module doesn't, project-level deps might apply to some modules,
            // but that logic is more complex (e.g. for IntelliJ native modules in a Maven/Gradle project).
            // For now, we only merge if the module itself has a parsed build file.

            module.copy(
                buildSystemInfo = moduleBuildSystemInfo ?: projectBuildSystemInfo, // Module takes precedence, else project's
                moduleGroupId = moduleGroupId,
                moduleVersion = moduleVersion,
                dependencies = finalDeps.distinctBy { it.name + it.version + it.scope } // Ensure uniqueness
            )
        }

        return IntelliJProjectDetails(
            projectName = projectName,
            projectRootPath = projectRoot.toString(),
            projectGroupId = projectGroupId,
            projectVersion = projectVersion,
            projectSdkName = projectSdkName,
            projectJdkVersion = projectJdkVersion,
            modules = finalModules,
            buildSystemInfo = projectBuildSystemInfo
        )
    }

    internal fun mergeDependencies(imlDeps: List<DependencyInfo>, buildFileDeps: List<DependencyInfo>): MutableList<DependencyInfo> { // Changed to internal
        val merged = imlDeps.toMutableList()
        val imlDepMap = imlDeps.associateBy { normalizeDependencyName(it.name) }.toMutableMap()

        for (buildDep in buildFileDeps) {
            val normalizedBuildDepName = normalizeDependencyName(buildDep.name)
            val existingImlDep = imlDepMap[normalizedBuildDepName]

            if (existingImlDep != null) {
                // Dependency exists in both. Prefer build file version/scope.
                val updatedDep = existingImlDep.copy(
                    version = buildDep.version ?: existingImlDep.version,
                    scope = buildDep.scope ?: existingImlDep.scope
                )
                val index = merged.indexOf(existingImlDep)
                if (index != -1) merged[index] = updatedDep
            } else {
                // Dependency only in build file, add it.
                merged.add(buildDep)
            }
        }
        return merged
    }

    // Helper to normalize dependency names for comparison, e.g. "Gradle: g:a" vs "g:a"
    internal fun normalizeDependencyName(name: String): String { // Changed to internal
        return name.substringAfter("Gradle: ").substringAfter("Maven: ")
    }


    internal fun locateAndParseBuildSystem(
        directory: Path,
        projectRoot: Path,
        isModuleScan: Boolean = false,
        projectInfoConsumer: (Any) -> Unit = {} // Consumes MavenProjectInfo or GradleProjectInfo
    ): BuildSystemInfo? {
        val pomFile = directory.resolve("pom.xml")
        if (pomFile.toFile().exists()) {
            try {
                val mavenInfo = mavenParser.parse(pomFile)
                projectInfoConsumer(mavenInfo)
                return BuildSystemInfo(type = BuildSystemType.MAVEN, buildFilePath = pomFile.toString())
            } catch (e: Exception) {
                System.err.println("Failed to parse pom.xml ${pomFile}: ${e.message}")
                return BuildSystemInfo(type = BuildSystemType.MAVEN, buildFilePath = pomFile.toString()) // Still report its existence
            }
        }

        val gradleKtsFile = directory.resolve("build.gradle.kts")
        if (gradleKtsFile.toFile().exists()) {
            try {
                val gradleInfo = gradleParser.parse(gradleKtsFile)
                projectInfoConsumer(gradleInfo)
                return BuildSystemInfo(type = BuildSystemType.GRADLE, buildFilePath = gradleKtsFile.toString())
            } catch (e: Exception) {
                System.err.println("Failed to parse build.gradle.kts ${gradleKtsFile}: ${e.message}")
                 return BuildSystemInfo(type = BuildSystemType.GRADLE, buildFilePath = gradleKtsFile.toString())
            }
        }

        val gradleFile = directory.resolve("build.gradle")
        if (gradleFile.toFile().exists()) {
             try {
                val gradleInfo = gradleParser.parse(gradleFile)
                projectInfoConsumer(gradleInfo)
                return BuildSystemInfo(type = BuildSystemType.GRADLE, buildFilePath = gradleFile.toString())
            } catch (e: Exception) {
                System.err.println("Failed to parse build.gradle ${gradleFile}: ${e.message}")
                return BuildSystemInfo(type = BuildSystemType.GRADLE, buildFilePath = gradleFile.toString())
            }
        }

        return if (isModuleScan) null else BuildSystemInfo(type = BuildSystemType.INTELLIJ_NATIVE)
    }


    internal fun parseProjectName(ideaDir: Path, projectRoot: Path): String { // Changed to internal
        val nameFile = ideaDir.resolve(".name")
        return if (nameFile.toFile().exists()) {
            nameFile.toFile().readText().trim()
        } else {
            projectRoot.fileName.toString()
        }
    }

    internal fun resolvePath(pathStr: String?, projectRoot: Path, moduleDir: Path? = null): String? {
        if (pathStr == null) return null
        var resolved = pathStr.replace("\$PROJECT_DIR\$", projectRoot.toString())
        if (moduleDir != null) {
            resolved = resolved.replace("\$MODULE_DIR\$", moduleDir.toString())
        }
        return Paths.get(resolved.removePrefix("file://")).toAbsolutePath().toString()
    }

    internal fun parseModulesXml(ideaDir: Path, projectRoot: Path): List<Path> { // Changed to internal
        val modulesXmlFile = ideaDir.resolve("modules.xml")
        if (!modulesXmlFile.toFile().exists()) {
            System.err.println("Warning: modules.xml not found at $modulesXmlFile")
            return emptyList()
        }

        val imlPaths = mutableListOf<Path>()
        try {
            val reader = xmlInputFactory.createXMLEventReader(FileInputStream(modulesXmlFile.toFile()))
            while (reader.hasNext()) {
                val event = reader.nextEvent()
                if (event.isStartElement) {
                    val startElement = event.asStartElement()
                    if (startElement.name.localPart == "module") {
                        val filepathAttr = startElement.getAttributeByName(null, "filepath")
                        if (filepathAttr != null) {
                            resolvePath(filepathAttr.value, projectRoot)?.let {
                                imlPaths.add(Paths.get(it))
                            }
                        }
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            System.err.println("Error parsing modules.xml: ${e.message}")
            // Return any paths found so far, or empty list
        }
        return imlPaths
    }

    internal fun parseProjectSdk(ideaDir: Path): Pair<String?, String?> {
        val miscXmlFile = ideaDir.resolve("misc.xml") // Common location
        // Other potential files: projectRootManager.xml, jdk.table.xml (more complex)
        // For simplicity, focusing on misc.xml's ProjectRootManager component first.

        if (!miscXmlFile.toFile().exists()) {
             System.err.println("Warning: misc.xml not found at $miscXmlFile. Project SDK info might be missing.")
            return Pair(null, null)
        }

        var jdkName: String? = null
        var jdkType: String? = null // Not directly projectJdkVersion, but related

        try {
            val reader = xmlInputFactory.createXMLEventReader(FileInputStream(miscXmlFile.toFile()))
            while(reader.hasNext()) {
                val event = reader.nextEvent()
                if (event.isStartElement) {
                    val startElement = event.asStartElement()
                    if (startElement.name.localPart == "component" &&
                        startElement.getAttributeByName(null, "name")?.value == "ProjectRootManager") {
                        jdkName = startElement.getAttributeByName(null, "project-jdk-name")?.value
                        jdkType = startElement.getAttributeByName(null, "project-jdk-type")?.value
                        // project-jdk-version is not usually here, jdkName is what's used.
                        // Actual version string is often part of jdkName or resolved via JDK table.
                        break // Found the component
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            System.err.println("Error parsing misc.xml for Project SDK: ${e.message}")
        }
        // We return jdkName as projectSdkName. The version might be part of this name or requires deeper inspection.
        return Pair(jdkName, jdkName) // Using jdkName for both name and "version" for now.
    }

    // parseProjectSdk remains private, tested via parseProject integration.

    internal fun parseImlFile(imlFilePath: Path, projectRoot: Path): ModuleDetails { // Changed to internal
        if (!imlFilePath.toFile().exists()) {
            throw IllegalArgumentException("IML file does not exist: $imlFilePath")
        }
        val moduleName = imlFilePath.fileName.toString().removeSuffix(".iml")
        val moduleDir = imlFilePath.parent

        val sourceDirs = mutableListOf<String>()
        val resourceDirs = mutableListOf<String>()
        val testSourceDirs = mutableListOf<String>()
        val testResourceDirs = mutableListOf<String>()
        var moduleSdkName: String? = null
        // var moduleJdkVersion: String? = null // Usually part of moduleSdkName or resolved via table
        var inheritedJdk = false
        val dependencies = mutableListOf<DependencyInfo>()

        try {
            val reader = xmlInputFactory.createXMLEventReader(FileInputStream(imlFilePath.toFile()))
            var isNewModuleRootManager = false
            var currentContentUrl: Path? = null

            while(reader.hasNext()) {
                val event = reader.nextEvent()
                when {
                    event.isStartElement -> {
                        val start = event.asStartElement()
                        when (start.name.localPart) {
                            "component" -> {
                                if (start.getAttributeByName(null, "name")?.value == "NewModuleRootManager") {
                                    isNewModuleRootManager = true
                                }
                            }
                            "content" -> {
                                if (isNewModuleRootManager) {
                                     val url = start.getAttributeByName(null, "url")?.value
                                     currentContentUrl = url?.let { Paths.get(resolvePath(it, projectRoot, moduleDir) ?: it) }
                                }
                            }
                            "sourceFolder" -> {
                                if (isNewModuleRootManager) {
                                    val url = start.getAttributeByName(null, "url")?.value
                                    val resolvedUrl = url?.let { resolvePath(it, projectRoot, moduleDir ?: currentContentUrl) } ?: continue

                                    val isTestSource = start.getAttributeByName(null, "isTestSource")?.value.toBoolean()
                                    val type = start.getAttributeByName(null, "type")?.value

                                    when {
                                        isTestSource -> testSourceDirs.add(resolvedUrl)
                                        type == "java-resource" -> resourceDirs.add(resolvedUrl)
                                        type == "java-test-resource" -> testResourceDirs.add(resolvedUrl)
                                        else -> sourceDirs.add(resolvedUrl) // Default to source
                                    }
                                }
                            }
                            "orderEntry" -> {
                                if (isNewModuleRootManager) {
                                    parseOrderEntry(start, dependencies) { sdk, inherited ->
                                        moduleSdkName = sdk
                                        inheritedJdk = inherited
                                    }
                                }
                            }
                        }
                    }
                    event.isEndElement -> {
                        val end = event.asEndElement()
                        if (end.name.localPart == "component" && isNewModuleRootManager) {
                            isNewModuleRootManager = false // Exit component scope
                        } else if (end.name.localPart == "content" && isNewModuleRootManager) {
                            currentContentUrl = null // Exit content scope
                        }
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            System.err.println("Error parsing IML file $imlFilePath: ${e.message}")
            // Return what has been gathered so far or throw
        }

        if (inheritedJdk && moduleSdkName == null) {
            // If JDK is inherited and not overridden by a module-specific JDK orderEntry.
            // We don't have project SDK details directly here, so this indicates it uses project's.
            // The final IntelliJProjectDetails object will show the project SDK.
        }

        return ModuleDetails(
            moduleName = moduleName,
            imlPath = imlFilePath.toString(),
            sourceDirs = sourceDirs.distinct(),
            resourceDirs = resourceDirs.distinct(),
            testSourceDirs = testSourceDirs.distinct(),
            testResourceDirs = testResourceDirs.distinct(),
            moduleSdkName = moduleSdkName,
            moduleJdkVersion = moduleSdkName, // Using sdkName as version placeholder for now
            dependencies = dependencies.distinct()
            // buildSystemInfo will be handled later
        )
    }

    internal fun parseOrderEntry(element: StartElement, dependencies: MutableList<DependencyInfo>, sdkInfoConsumer: (String?, Boolean) -> Unit) {
        val type = element.getAttributeByName(null, "type")?.value ?: return
        when (type) {
            "jdk" -> {
                val jdkName = element.getAttributeByName(null, "jdkName")?.value
                // val jdkType = element.getAttributeByName(null, "jdkType")?.value
                sdkInfoConsumer(jdkName, false)
            }
            "inheritedJdk" -> {
                sdkInfoConsumer(null, true)
            }
            "library" -> {
                val name = element.getAttributeByName(null, "name")?.value
                val scope = element.getAttributeByName(null, "scope")?.value
                // val level = element.getAttributeByName(null, "level")?.value // e.g. project, module
                if (name != null) {
                    // Version parsing from name is complex (e.g., "Gradle: org.foo:bar:1.0", "Maven: org.foo:bar:1.0", or just "mylib")
                    // Basic approach: if it contains ':', it might be a Maven/Gradle style coordinate.
                    val parts = name.split(':')
                    val depName = if (parts.size >= 2) parts.take(2).joinToString(":") else name
                    val version = if (parts.size >= 3) parts.getOrNull(2) else null

                    dependencies.add(DependencyInfo(name = depName, version = version, scope = scope, type = DependencyType.LIBRARY))
                }
            }
            "module" -> {
                val moduleName = element.getAttributeByName(null, "module-name")?.value
                val scope = element.getAttributeByName(null, "scope")?.value
                if (moduleName != null) {
                    dependencies.add(DependencyInfo(name = moduleName, scope = scope, type = DependencyType.MODULE))
                }
            }
            // "sourceFolder" type orderEntry also exists, but we handle sources from <content>
        }
    }
}
