package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.toClassName
import com.squareup.kotlinpoet.*
import java.io.OutputStream

class TrikeShedDslProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return TrikeShedDslProcessor(environment)
    }
}

class TrikeShedDslProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dslSymbols = resolver.getSymbolsWithAnnotation(TrikeShedDsl::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (!dslSymbols.iterator().hasNext()) return emptyList()

        logger.info("Found ${dslSymbols.count()} DSL components.")

        val fileSpec = FileSpec.builder("borg.trikeshed.dsl", "DeepDsl")
            .addImport("borg.trikeshed.lib", "Indexed")
            .addType(TypeSpec.classBuilder("TrikeShedDslContext").build())
            .addFunction(
                FunSpec.builder("trikeshed")
                    .addParameter("block", LambdaTypeName.get(receiver = ClassName("borg.trikeshed.dsl", "TrikeShedDslContext"), returnType = UNIT))
                    .addStatement("TrikeShedDslContext().block()")
                    .build()
            )

        dslSymbols.forEach { dslSymbol ->
            val dslAnnotation = dslSymbol.annotations.first { it.shortName.asString() == "TrikeShedDsl" }
            val dslNameArg = dslAnnotation.arguments.find { it.name?.asString() == "name" }
            val dslName = (dslNameArg?.value as? String)?.takeIf { it.isNotBlank() }
                ?: dslSymbol.simpleName.asString().removeSuffix("Config").replaceFirstChar { it.lowercase() }

            logger.info("Generating DSL for '$dslName' from ${dslSymbol.qualifiedName?.asString()}")

            val configClassName = dslSymbol.toClassName()
            val dslContextClassName = ClassName("borg.trikeshed.dsl", "TrikeShedDslContext")

            val funSpec = FunSpec.builder(dslName)
                .receiver(dslContextClassName)
                .addParameter("block", LambdaTypeName.get(receiver = configClassName, returnType = UNIT))
                .addStatement("%T().apply(block)", configClassName)
                .build()
            
            fileSpec.addFunction(funSpec)
        }

        val generatedFile = fileSpec.build()
        
        val outputStream = codeGenerator.createNewFile(
            dependencies = Dependencies(true, *dslSymbols.mapNotNull { it.containingFile }.toList().toTypedArray()),
            packageName = "borg.trikeshed.dsl",
            fileName = "DeepDsl"
        )
        
        outputStream.writer().use {
            generatedFile.writeTo(it)
        }

        return emptyList()
    }
} 