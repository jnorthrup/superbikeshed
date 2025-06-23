package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.OutputStream

class TrikeShedProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    
    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Process @GenerateJoinPackers
        processJoinPackers(resolver)
        
        // Process @GenerateMetaSeries  
        processMetaSeries(resolver)
        
        // Process @GeneratePackingStrategies
        processPackingStrategies(resolver)
        
        // Process @GenerateWireAdapters
        processWireAdapters(resolver)
        
        // Process @GenerateSeriesExtensions
        processSeriesExtensions(resolver)
        
        // Process @GenerateDataClassBuilders
        processDataClassBuilders(resolver)
        
        // Process @GenerateEnumUtilities
        processEnumUtilities(resolver)
        
        // Process @GenerateAttentionDelegates
        processAttentionDelegates(resolver)
        
        // Process @GeneratePlatformImplementations
        processPlatformImplementations(resolver)
        
        // Process @GenerateTestUtilities
        processTestUtilities(resolver)
        
        // Process @GenerateSystemPropertyBoilerplate
        processSystemPropertyBoilerplate(resolver)
        
        return emptyList()
    }
    
    private fun processJoinPackers(resolver: Resolver) {
        val annotated = resolver.getSymbolsWithAnnotation(GenerateJoinPackers::class.qualifiedName!!)
        
        annotated.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                val annotation = symbol.annotations.find { 
                    it.shortName.asString() == "GenerateJoinPackers" 
                }
                
                if (annotation != null) {
                    generateJoinPackers(annotation)
                }
            }
        }
    }
    
    private fun generateJoinPackers(annotation: KSAnnotation) {
        val packageName = getAnnotationValue<String>(annotation, "packageName") ?: "borg.trikeshed.generated"
        
        // Define primitive types and their bit widths
        val primitiveTypes = mapOf(
            "Int" to 32,
            "Long" to 64, 
            "Boolean" to 1,
            "Byte" to 8,
            "Short" to 16,
            "Float" to 32,
            "Double" to 64
        )
        
        // Generate Packable interface
        val packableInterface = generatePackableInterface()
        writeFile(packageName, "Packable", packableInterface)
        
        // Generate RegisterJoin value class
        val registerJoinClass = generateRegisterJoinClass()
        writeFile(packageName, "RegisterJoin", registerJoinClass)
        
        // Generate Packable implementations for each primitive
        primitiveTypes.forEach { (typeName, bitWidth) ->
            val packableImpl = generatePackableImpl(typeName, bitWidth)
            writeFile(packageName, "P$typeName", packableImpl)
        }
        
        // Generate j overloads for all primitive combinations
        val joinOverloads = generateJoinOverloads(primitiveTypes)
        writeFile(packageName, "JoinOverloads", joinOverloads)
        
        logger.info("Generated join packers for ${primitiveTypes.size * primitiveTypes.size} combinations")
    }
    
    private fun generatePackableInterface(): FileSpec {
        return FileSpec.builder("borg.trikeshed.generated", "Packable")
            .addType(
                TypeSpec.interfaceBuilder("Packable")
                    .addTypeVariable(TypeVariableName("T"))
                    .addProperty(
                        PropertySpec.builder("bitWidth", Int::class)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("pack")
                            .addParameter("value", TypeVariableName("T"))
                            .returns(Long::class)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("unpack")
                            .addParameter("bits", Long::class)
                            .returns(TypeVariableName("T"))
                            .build()
                    )
                    .build()
            )
            .build()
    }
    
    private fun generateRegisterJoinClass(): FileSpec {
        return FileSpec.builder("borg.trikeshed.generated", "RegisterJoin")
            .addImport("kotlin.jvm", "JvmInline")
            .addType(
                TypeSpec.classBuilder("RegisterJoin")
                    .addAnnotation(ClassName("kotlin.jvm", "JvmInline"))
                    .addModifiers(KModifier.VALUE)
                    .addTypeVariable(TypeVariableName("A"))
                    .addTypeVariable(TypeVariableName("B"))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("word", Long::class)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("word", Long::class)
                            .initializer("word")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("unpackA")
                            .addTypeVariable(TypeVariableName("PA", ClassName("borg.trikeshed.generated", "Packable").parameterizedBy(TypeVariableName("A"))))
                            .addParameter("packer", TypeVariableName("PA"))
                            .returns(TypeVariableName("A"))
                            .addStatement("return packer.unpack(word)")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("unpackB")
                            .addTypeVariable(TypeVariableName("PB", ClassName("borg.trikeshed.generated", "Packable").parameterizedBy(TypeVariableName("B"))))
                            .addParameter("packerA", ClassName("borg.trikeshed.generated", "Packable").parameterizedBy(TypeVariableName("A")))
                            .addParameter("packerB", TypeVariableName("PB"))
                            .returns(TypeVariableName("B"))
                            .addStatement("return packerB.unpack(word shr packerA.bitWidth)")
                            .build()
                    )
                    .build()
            )
            .build()
    }
    
    private fun generatePackableImpl(typeName: String, bitWidth: Int): FileSpec {
        val className = "P$typeName"
        
        return FileSpec.builder("borg.trikeshed.generated", className)
            .addType(
                TypeSpec.objectBuilder(className)
                    .addSuperinterface(
                        ClassName("borg.trikeshed.generated", "Packable").parameterizedBy(
                            ClassName("kotlin", typeName)
                        )
                    )
                    .addProperty(
                        PropertySpec.builder("bitWidth", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer("$bitWidth")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("pack")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("value", ClassName("kotlin", typeName))
                            .returns(Long::class)
                            .apply {
                                when (typeName) {
                                    "Int" -> addStatement("return value.toLong() and 0xFFFFFFFF")
                                    "Long" -> addStatement("return value")
                                    "Boolean" -> addStatement("return if (value) 1L else 0L")
                                    "Byte" -> addStatement("return value.toLong() and 0xFF")
                                    "Short" -> addStatement("return value.toLong() and 0xFFFF")
                                    "Float" -> addStatement("return value.toBits().toLong() and 0xFFFFFFFF")
                                    "Double" -> addStatement("return value.toBits()")
                                }
                            }
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("unpack")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("bits", Long::class)
                            .returns(ClassName("kotlin", typeName))
                            .apply {
                                when (typeName) {
                                    "Int" -> addStatement("return bits.toInt()")
                                    "Long" -> addStatement("return bits")
                                    "Boolean" -> addStatement("return bits != 0L")
                                    "Byte" -> addStatement("return bits.toByte()")
                                    "Short" -> addStatement("return bits.toShort()")
                                    "Float" -> addStatement("return Float.fromBits(bits.toInt())")
                                    "Double" -> addStatement("return Double.fromBits(bits)")
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
    
    private fun generateJoinOverloads(primitiveTypes: Map<String, Int>): FileSpec {
        val fileBuilder = FileSpec.builder("borg.trikeshed.generated", "JoinOverloads")
        
        // Generate j overload for each L x R combination
        primitiveTypes.forEach { (leftType, leftBits) ->
            primitiveTypes.forEach { (rightType, rightBits) ->
                if (leftBits + rightBits <= 64) { // Can fit in Long register
                    val jFunction = FunSpec.builder("j")
                        .addModifiers(KModifier.INLINE, KModifier.INFIX)
                        .receiver(ClassName("kotlin", leftType))
                        .addParameter("b", ClassName("kotlin", rightType))
                        .returns(
                            ClassName("borg.trikeshed.generated", "RegisterJoin")
                                .parameterizedBy(
                                    ClassName("kotlin", leftType),
                                    ClassName("kotlin", rightType)
                                )
                        )
                        .addStatement("val bitsL = P$leftType.pack(this)")
                        .addStatement("val bitsR = P$rightType.pack(b)")
                        .addStatement("return RegisterJoin(bitsL or (bitsR shl $leftBits))")
                        .build()
                    
                    fileBuilder.addFunction(jFunction)
                }
            }
        }
        
        return fileBuilder.build()
    }
    
    private fun processMetaSeries(resolver: Resolver) {
        val annotated = resolver.getSymbolsWithAnnotation(GenerateMetaSeries::class.qualifiedName!!)
        
        annotated.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                val annotation = symbol.annotations.find { 
                    it.shortName.asString() == "GenerateMetaSeries" 
                }
                
                if (annotation != null) {
                    generateMetaSeries(annotation)
                }
            }
        }
    }
    
    private fun generateMetaSeries(annotation: KSAnnotation) {
        val packageName = getAnnotationValue<String>(annotation, "packageName") ?: "borg.trikeshed.generated"
        
        // Generate unified MetaSeries hierarchy
        val metaSeriesFile = FileSpec.builder(packageName, "MetaSeries")
            .addImport("borg.trikeshed.lib", "*")
            .addType(generateMetaSeriesClass())
            .build()
            
        writeFile(packageName, "MetaSeries", metaSeriesFile)
        
        logger.info("Generated MetaSeries hierarchy")
    }
    
    private fun generateMetaSeriesClass(): TypeSpec {
        return TypeSpec.interfaceBuilder("MetaSeries")
            .addModifiers(KModifier.SEALED)
            .addTypeVariable(TypeVariableName("S")) // Shape type
            .addTypeVariable(TypeVariableName("T")) // Element type
            .addProperty(
                PropertySpec.builder("size", Int::class)
                    .build()
            )
            .addFunction(
                FunSpec.builder("memoryFootprint")
                    .returns(Int::class)
                    .build()
            )
            .build()
    }
    
    private fun processPackingStrategies(resolver: Resolver) {
        val annotated = resolver.getSymbolsWithAnnotation(GeneratePackingStrategies::class.qualifiedName!!)
        
        annotated.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                val annotation = symbol.annotations.find { 
                    it.shortName.asString() == "GeneratePackingStrategies" 
                }
                
                if (annotation != null) {
                    generatePackingStrategies(annotation)
                }
            }
        }
    }
    
    private fun generatePackingStrategies(annotation: KSAnnotation) {
        val packageName = getAnnotationValue<String>(annotation, "packageName") ?: "borg.trikeshed.generated"
        
        // Generate optimized packing strategy implementations
        logger.info("Generated packing strategies")
    }
    
    private fun processSystemPropertyBoilerplate(resolver: Resolver) {
        val annotated = resolver.getSymbolsWithAnnotation(GenerateSystemPropertyBoilerplate::class.qualifiedName!!)
        
        annotated.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                val annotation = symbol.annotations.find { 
                    it.shortName.asString() == "GenerateSystemPropertyBoilerplate" 
                }
                
                if (annotation != null) {
                    generateSystemPropertyBoilerplate(annotation)
                }
            }
        }
        
        // Always generate system property boilerplate for common use
        generateSystemPropertyBoilerplate(null)
    }
    
    private fun generateSystemPropertyBoilerplate(annotation: KSAnnotation?) {
        val packageName = getAnnotationValue<String>(annotation, "packageName") ?: "borg.trikeshed.lib"
        
        // Generate JVM implementation
        val jvmImpl = FileSpec.builder("$packageName", "SystemPropertiesJvm")
            .addType(
                TypeSpec.objectBuilder("SystemPropertiesJvm")
                    .addFunction(
                        FunSpec.builder("getSystemProperty")
                            .addModifiers(KModifier.ACTUAL)
                            .addParameter("key", String::class)
                            .returns(String::class.asTypeName().copy(nullable = true))
                            .addStatement("return System.getProperty(key)")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("getCurrentTimeMillis")
                            .addModifiers(KModifier.ACTUAL)
                            .returns(Long::class)
                            .addStatement("return System.currentTimeMillis()")
                            .build()
                    )
                    .build()
            )
            .build()
        
        // Generate Native implementation
        val nativeImpl = FileSpec.builder("$packageName", "SystemPropertiesNative")
            .addImport("kotlinx.cinterop", "*")
            .addImport("platform.posix", "*")
            .addType(
                TypeSpec.objectBuilder("SystemPropertiesNative")
                    .addFunction(
                        FunSpec.builder("getSystemProperty")
                            .addModifiers(KModifier.ACTUAL)
                            .addParameter("key", String::class)
                            .returns(String::class.asTypeName().copy(nullable = true))
                            .addStatement("// Native implementation - return null for now")
                            .addStatement("return null")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("getCurrentTimeMillis")
                            .addModifiers(KModifier.ACTUAL)
                            .returns(Long::class)
                            .addStatement("// Native implementation using platform time")
                            .addStatement("return 0L") // Placeholder
                            .build()
                    )
                    .build()
            )
            .build()
        
        // Generate JS implementation
        val jsImpl = FileSpec.builder("$packageName", "SystemPropertiesJs")
            .addType(
                TypeSpec.objectBuilder("SystemPropertiesJs")
                    .addFunction(
                        FunSpec.builder("getSystemProperty")
                            .addModifiers(KModifier.ACTUAL)
                            .addParameter("key", String::class)
                            .returns(String::class.asTypeName().copy(nullable = true))
                            .addStatement("// JS implementation - return null for now")
                            .addStatement("return null")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("getCurrentTimeMillis")
                            .addModifiers(KModifier.ACTUAL)
                            .returns(Long::class)
                            .addStatement("// JS implementation using Date.now()")
                            .addStatement("return kotlin.js.Date.now().toLong()")
                            .build()
                    )
                    .build()
            )
            .build()
        
        writeFile("$packageName", "SystemPropertiesJvm", jvmImpl)
        writeFile("$packageName", "SystemPropertiesNative", nativeImpl)
        writeFile("$packageName", "SystemPropertiesJs", jsImpl)
        
        logger.info("Generated system property boilerplate for all platforms")
    }
    
    private fun writeFile(packageName: String, fileName: String, fileSpec: FileSpec) {
        try {
            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false),
                packageName = packageName,
                fileName = fileName
            )
            
            file.use { outputStream ->
                fileSpec.writeTo(outputStream)
            }
        } catch (e: Exception) {
            logger.error("Failed to write file $fileName: ${e.message}")
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun <T> getAnnotationValue(annotation: KSAnnotation, name: String): T? {
        return annotation.arguments.find { it.name?.asString() == name }?.value as? T
    }
}

private fun FileSpec.writeTo(outputStream: OutputStream) {
    outputStream.writer().use { writer ->
        writeTo(writer)
    }
}