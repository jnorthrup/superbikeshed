package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSVisitorVoid
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * KSP Processor for generating optimal register packing dispatch
 * 
 * This processor analyzes classes annotated with @OptimizeRegisterPacking
 * and generates type-specific packing strategies based on the key/value types.
 */
class RegisterPackingProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RegisterPackingProcessor(environment)
    }
}

class RegisterPackingProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {
    
    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(
            "borg.trikeshed.collections.OptimizeRegisterPacking"
        ).filterIsInstance<KSClassDeclaration>()
        
        if (!annotatedClasses.iterator().hasNext()) return emptyList()
        
        annotatedClasses.forEach { classDecl ->
            generatePackingDispatch(classDecl)
        }
        
        return emptyList()
    }
    
    private fun generatePackingDispatch(classDecl: KSClassDeclaration) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        
        // Find the key and value type parameters
        val typeParams = classDecl.typeParameters
        if (typeParams.size < 2) {
            logger.error("@OptimizeRegisterPacking requires at least 2 type parameters", classDecl)
            return
        }
        
        val keyTypeParam = typeParams[0]
        val valueTypeParam = typeParams[1]
        
        val fileSpec = FileSpec.builder(packageName, "${className}PackingDispatch")
            .addImport("borg.trikeshed.wireproto", "RegisterJoin")
            .addImport("borg.trikeshed.wireproto", "j")
            .addImport("borg.trikeshed.collections", "TreeMapNode")
            .addImport("kotlin.coroutines", "CoroutineContext")
            
        // Generate the packing dispatcher function
        val dispatcherFunc = FunSpec.builder("create${className}Node")
            .addModifiers(KModifier.INLINE)
            .addTypeVariable(keyTypeParam.toTypeVariableName())
            .addTypeVariable(valueTypeParam.toTypeVariableName())
            .addParameter("key", keyTypeParam.toTypeVariableName())
            .addParameter("value", valueTypeParam.toTypeVariableName())
            .addParameter(
                ParameterSpec.builder("context", CoroutineContext::class)
                    .defaultValue("CoroutineContext.Empty")
                    .build()
            )
            .returns(Any::class)
            .beginControlFlow("return when")
            .addStatement("key is Int && value is Int -> key j value")
            .addStatement("key is Int && value is Boolean -> key j value")
            .addStatement("key is Int && value is Byte -> key j value")
            .addStatement("key is Int && value is Short -> key j value")
            .addStatement("key is Short && value is Short -> key j value")
            .addStatement("key is Byte && value is Byte -> key j value")
            .addStatement("key is Boolean && value is Boolean -> key j value")
            .addStatement("key is Boolean && value is Int -> key j value")
            .addStatement("key is Float && value is Float -> key j value")
            .addStatement("else -> TreeMapNode(key, value)")
            .endControlFlow()
            .build()
            
        fileSpec.addFunction(dispatcherFunc)
        
        // Generate unpacking functions for each supported type combination
        generateUnpackingFunctions(fileSpec, keyTypeParam, valueTypeParam)
        
        // Write the generated file
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false, classDecl.containingFile!!),
            packageName = packageName,
            fileName = "${className}PackingDispatch"
        )
        
        file.writer().use { writer ->
            fileSpec.build().writeTo(writer)
        }
    }
    
    private fun generateUnpackingFunctions(
        fileSpec: FileSpec.Builder,
        keyTypeParam: KSTypeParameter,
        valueTypeParam: KSTypeParameter
    ) {
        // Generate type-safe unpacking extensions
        val packedTypes = listOf(
            "Int" to "Int",
            "Int" to "Boolean", 
            "Int" to "Byte",
            "Int" to "Short",
            "Short" to "Short",
            "Byte" to "Byte",
            "Boolean" to "Boolean",
            "Boolean" to "Int",
            "Float" to "Float"
        )
        
        packedTypes.forEach { (keyType, valueType) ->
            val unpackKeyFunc = FunSpec.builder("unpack${keyType}${valueType}Key")
                .receiver(
                    ClassName("borg.trikeshed.wireproto", "RegisterJoin")
                        .parameterizedBy(
                            ClassName("kotlin", keyType),
                            ClassName("kotlin", valueType)
                        )
                )
                .returns(ClassName("kotlin", keyType))
                .addStatement("return this.unpackA(P$keyType)")
                .build()
                
            val unpackValueFunc = FunSpec.builder("unpack${keyType}${valueType}Value")
                .receiver(
                    ClassName("borg.trikeshed.wireproto", "RegisterJoin")
                        .parameterizedBy(
                            ClassName("kotlin", keyType),
                            ClassName("kotlin", valueType)
                        )
                )
                .returns(ClassName("kotlin", valueType))
                .addStatement("return this.unpackB(P$keyType, P$valueType)")
                .build()
                
            fileSpec.addFunction(unpackKeyFunc)
            fileSpec.addFunction(unpackValueFunc)
        }
    }
    
    private fun KSTypeParameter.toTypeVariableName(): TypeVariableName {
        return TypeVariableName(name.asString())
    }
}