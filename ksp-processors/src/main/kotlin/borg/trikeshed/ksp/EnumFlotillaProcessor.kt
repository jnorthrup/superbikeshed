package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

class EnumFlotillaProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.warn("EnumFlotillaProcessor: Starting agglomerated view generation")
        
        // Collect different types of annotated classes
        val handlers = collectAnnotatedClasses(resolver, "Handler")
        val services = collectAnnotatedClasses(resolver, "Service") 
        val plugins = collectAnnotatedClasses(resolver, "Plugin")
        val workflows = collectAnnotatedClasses(resolver, "Workflow")
        val capabilities = collectAnnotatedClasses(resolver, "Capability")
        
        // Generate enum flotillas for each agglomerated view
        generateHandlerFlotilla(handlers)
        generateServiceFlotilla(services)
        generatePluginFlotilla(plugins)
        generateWorkflowFlotilla(workflows)
        generateCapabilityFlotilla(capabilities)
        
        // Generate master registry combining all flotillas
        generateMasterRegistry(handlers.size, services.size, plugins.size, workflows.size, capabilities.size)
        
        return emptyList()
    }
    
    private fun collectAnnotatedClasses(resolver: Resolver, annotationSuffix: String): List<KSClassDeclaration> {
        val annotationName = "borg.trikeshed.ksp.$annotationSuffix"
        return resolver.getSymbolsWithAnnotation(annotationName)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()
    }
    
    private fun generateHandlerFlotilla(handlers: List<KSClassDeclaration>) {
        val handlerEntries = handlers.joinToString(",\n") { handler ->
            val className = handler.qualifiedName?.asString() ?: handler.simpleName.asString()
            val capabilities = extractCapabilities(handler)
            val description = extractDescription(handler)
            val capabilityList = capabilities.joinToString(", ") { "\"$it\"" }
            
            """
            |    ${handler.simpleName.asString().uppercase()}(
            |        className = "$className",
            |        capabilities = Indexed($capabilityList),
            |        description = "$description"
            |    )""".trimMargin()
        }
        
        val enumContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |import borg.trikeshed.lib.Indexed
            |
            |@Serializable
            |enum class HandlerType(
            |    val className: String,
            |    val capabilities: Indexed<String>,
            |    val description: String
            |) {
            |$handlerEntries
            |}
            |""".trimMargin()
        
        writeGeneratedFile("HandlerType", enumContent)
    }
    
    private fun generateServiceFlotilla(services: List<KSClassDeclaration>) {
        val serviceEntries = services.joinToString(",\n") { service ->
            val className = service.qualifiedName?.asString() ?: service.simpleName.asString()
            val dependencies = extractDependencies(service)
            val version = extractVersion(service)
            val dependencyList = dependencies.joinToString(", ") { "\"$it\"" }
            
            """
            |    ${service.simpleName.asString().uppercase()}(
            |        className = "$className",
            |        dependencies = Indexed($dependencyList),
            |        version = "$version"
            |    )""".trimMargin()
        }
        
        val enumContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |import borg.trikeshed.lib.Indexed
            |
            |@Serializable
            |enum class ServiceType(
            |    val className: String,
            |    val dependencies: Indexed<String>,
            |    val version: String
            |) {
            |$serviceEntries
            |}
            |""".trimMargin()
        
        writeGeneratedFile("ServiceType", enumContent)
    }
    
    private fun generatePluginFlotilla(plugins: List<KSClassDeclaration>) {
        val pluginEntries = plugins.joinToString("\n") { plugin ->
            val name = extractPluginName(plugin)
            val version = extractVersion(plugin)
            val enabled = extractEnabled(plugin)
            
            """
            |    object ${plugin.simpleName.asString()} : PluginCapability(
            |        name = "$name",
            |        version = "$version",
            |        enabled = $enabled
            |    )""".trimMargin()
        }
        
        val enumContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |
            |@Serializable
            |sealed class PluginCapability(
            |    val name: String,
            |    val version: String,
            |    val enabled: Boolean
            |) {
            |$pluginEntries
            |}
            |""".trimMargin()
        
        writeGeneratedFile("PluginCapability", enumContent)
    }
    
    private fun generateWorkflowFlotilla(workflows: List<KSClassDeclaration>) {
        val workflowEntries = workflows.joinToString(",\n") { workflow ->
            val className = workflow.qualifiedName?.asString() ?: workflow.simpleName.asString()
            val triggers = extractTriggers(workflow)
            val priority = extractPriority(workflow)
            val triggerList = triggers.joinToString(", ") { "\"$it\"" }
            
            """
            |    ${workflow.simpleName.asString().uppercase()}(
            |        className = "$className",
            |        triggers = Indexed($triggerList),
            |        priority = $priority
            |    )""".trimMargin()
        }
        
        val enumContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |import borg.trikeshed.lib.Indexed
            |
            |@Serializable
            |enum class WorkflowType(
            |    val className: String,
            |    val triggers: Indexed<String>,
            |    val priority: Int
            |) {
            |$workflowEntries
            |}
            |""".trimMargin()
        
        writeGeneratedFile("WorkflowType", enumContent)
    }
    
    private fun generateCapabilityFlotilla(capabilities: List<KSClassDeclaration>) {
        val capabilityEntries = capabilities.joinToString(",\n") { capability ->
            val name = capability.simpleName.asString()
            val category = extractCategory(capability)
            val required = extractRequired(capability)
            
            """
            |    ${name.uppercase()}(
            |        name = "$name",
            |        category = "$category",
            |        required = $required
            |    )""".trimMargin()
        }
        
        val enumContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |
            |@Serializable
            |enum class CapabilityType(
            |    val name: String,
            |    val category: String,
            |    val required: Boolean
            |) {
            |$capabilityEntries
            |}
            |""".trimMargin()
        
        writeGeneratedFile("CapabilityType", enumContent)
    }
    
    private fun generateMasterRegistry(
        handlerCount: Int,
        serviceCount: Int,
        pluginCount: Int,
        workflowCount: Int,
        capabilityCount: Int
    ) {
        val registryContent = """
            |package borg.trikeshed.generated
            |
            |import kotlinx.serialization.Serializable
            |
            |/**
            | * Master registry providing a unified view of all agglomerated components.
            | * Generated by KSP - do not edit manually.
            | * 
            | * Total components: ${handlerCount + serviceCount + pluginCount + workflowCount + capabilityCount}
            | */
            |@Serializable
            |data class ComponentRegistry(
            |    val handlers: Int = $handlerCount,
            |    val services: Int = $serviceCount,
            |    val plugins: Int = $pluginCount,
            |    val workflows: Int = $workflowCount,
            |    val capabilities: Int = $capabilityCount
            |) {
            |    companion object {
            |        fun allHandlers() = HandlerType.values()
            |        fun allServices() = ServiceType.values()
            |        fun allPlugins() = PluginCapability::class.sealedSubclasses
            |        fun allWorkflows() = WorkflowType.values()
            |        fun allCapabilities() = CapabilityType.values()
            |        
            |        fun findHandler(name: String) = HandlerType.values().find { it.simpleName == name }
            |        fun findService(name: String) = ServiceType.values().find { it.className.endsWith(name) }
            |        fun findWorkflow(name: String) = WorkflowType.values().find { it.className.endsWith(name) }
            |        fun findCapability(name: String) = CapabilityType.values().find { it.name == name }
            |    }
            |}
            |""".trimMargin()
        
        writeGeneratedFile("ComponentRegistry", registryContent)
    }
    
    private fun writeGeneratedFile(name: String, content: String) {
        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "borg.trikeshed.generated",
            name,
            "kt"
        )
        file.writer().use { it.write(content) }
        logger.warn("EnumFlotillaProcessor: Generated $name.kt")
    }
    
    // Helper functions to extract metadata from annotations
    private fun extractCapabilities(classDecl: KSClassDeclaration): List<String> {
        val handlerAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Handler" 
        }
        
        val capabilities = handlerAnnotation?.arguments?.find { 
            it.name?.asString() == "capabilities" 
        }?.value as? List<*>
        
        return capabilities?.mapNotNull { it as? String } ?: listOf("DEFAULT")
    }
    
    private fun extractDescription(classDecl: KSClassDeclaration): String {
        val handlerAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Handler" 
        }
        
        val description = handlerAnnotation?.arguments?.find { 
            it.name?.asString() == "description" 
        }?.value as? String
        
        return description ?: classDecl.simpleName.asString()
    }
    
    private fun extractDependencies(classDecl: KSClassDeclaration): List<String> {
        val serviceAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Service" 
        }
        
        val dependencies = serviceAnnotation?.arguments?.find { 
            it.name?.asString() == "dependencies" 
        }?.value as? List<*>
        
        return dependencies?.mapNotNull { it as? String } ?: listOf("NONE")
    }
    
    private fun extractVersion(classDecl: KSClassDeclaration): String {
        val annotation = classDecl.annotations.find { 
            it.shortName.asString() in listOf("Service", "Plugin")
        }
        
        val version = annotation?.arguments?.find { 
            it.name?.asString() == "version" 
        }?.value as? String
        
        return version ?: "1.0.0"
    }
    
    private fun extractPluginName(classDecl: KSClassDeclaration): String {
        val pluginAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Plugin" 
        }
        
        val name = pluginAnnotation?.arguments?.find { 
            it.name?.asString() == "name" 
        }?.value as? String
        
        return name ?: classDecl.simpleName.asString().lowercase()
    }
    
    private fun extractEnabled(classDecl: KSClassDeclaration): Boolean {
        val pluginAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Plugin" 
        }
        
        val enabled = pluginAnnotation?.arguments?.find { 
            it.name?.asString() == "enabled" 
        }?.value as? Boolean
        
        return enabled ?: true
    }
    
    private fun extractTriggers(classDecl: KSClassDeclaration): List<String> {
        val workflowAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Workflow" 
        }
        
        val triggers = workflowAnnotation?.arguments?.find { 
            it.name?.asString() == "triggers" 
        }?.value as? List<*>
        
        return triggers?.mapNotNull { it as? String } ?: listOf("MANUAL")
    }
    
    private fun extractPriority(classDecl: KSClassDeclaration): Int {
        val workflowAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Workflow" 
        }
        
        val priority = workflowAnnotation?.arguments?.find { 
            it.name?.asString() == "priority" 
        }?.value as? Int
        
        return priority ?: 0
    }
    
    private fun extractCategory(classDecl: KSClassDeclaration): String {
        val capabilityAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Capability" 
        }
        
        val category = capabilityAnnotation?.arguments?.find { 
            it.name?.asString() == "category" 
        }?.value as? String
        
        return category ?: "GENERAL"
    }
    
    private fun extractRequired(classDecl: KSClassDeclaration): Boolean {
        val capabilityAnnotation = classDecl.annotations.find { 
            it.shortName.asString() == "Capability" 
        }
        
        val required = capabilityAnnotation?.arguments?.find { 
            it.name?.asString() == "required" 
        }?.value as? Boolean
        
        return required ?: false
    }
}

class EnumFlotillaProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return EnumFlotillaProcessor(environment.codeGenerator, environment.logger)
    }
} 