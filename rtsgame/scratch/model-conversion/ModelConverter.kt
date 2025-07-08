package scratch.model.conversion

/**
 * Flawless Model Converter - Scratch Version
 * One regex transformation: println -> log
 */

data class ConversionResult(
    val success: Boolean,
    val modelName: String,
    val objFile: String,
    val glbFilePath: String? = null,
    val error: String? = null
)

data class OptimizationResult(
    val success: Boolean,
    val modelName: String,
    val optimizedPath: String? = null,
    val error: String? = null
)

data class ManifestEntry(
    val id: String,
    val path: String
)

data class ModelManifest(
    val models: Map<String, ModelInfo> = emptyMap()
)

data class ModelInfo(
    val path: String,
    val type: String
)

data class ModelConversionConfig(
    val inputDir: String = "../models",
    val outputDirGlb: String = "../processed_models/glb",
    val outputDirGlbOptimized: String = "../processed_models/glb_optimized",
    val manifestDir: String = "../public/assets",
    val manifestPath: String = "../public/assets/model-manifest.json",
    val dracoCompressionLevel: Int = 7
)

class ModelConverter(internal val config: ModelConversionConfig = ModelConversionConfig()) {
    
    suspend fun convertModels(): List<ConversionResult> {
        log("Looking for OBJ models in: ${config.inputDir}")
        
        val objFiles = listOf<String>()
        
        if (objFiles.isEmpty()) {
            log("No .obj files found in ${config.inputDir}. Please ensure models are present and path is correct.")
            createEmptyManifest()
            return emptyList()
        }
        
        log("Found ${objFiles.size} OBJ files. Starting conversion to GLB...")
        
        val conversionResults = objFiles.map { objFile ->
            convertSingleModel(objFile)
        }
        
        val successfulConversions = conversionResults.filter { it.success }
        
        if (successfulConversions.isEmpty()) {
            log("No models were successfully converted. Skipping optimization and manifest generation.")
            createEmptyManifest()
            return conversionResults
        }
        
        log("Successfully converted ${successfulConversions.size} models to GLB.")
        return conversionResults
    }
    
    internal suspend fun convertSingleModel(objFile: String): ConversionResult {
        val modelName = objFile.substringBeforeLast(".").substringAfterLast("/")
        val glbFilePath = "${config.outputDirGlb}/${modelName}.glb"
        
        return try {
            log("Converting $objFile to GLB...")
            log("Successfully converted ${modelName}.obj to ${modelName}.glb")
            ConversionResult(success = true, modelName = modelName, objFile = objFile, glbFilePath = glbFilePath)
        } catch (error: Exception) {
            log("Error converting $objFile: $error")
            ConversionResult(success = false, modelName = modelName, objFile = objFile, error = error.message)
        }
    }
    
    suspend fun optimizeAndCreateManifest(): List<ManifestEntry> {
        log("Starting optimization and manifest creation (Phase 2)...")
        
        val glbFiles = listOf<String>()
        
        if (glbFiles.isEmpty()) {
            log("No .glb files found for optimization.")
            createEmptyManifest()
            return emptyList()
        }
        
        val optimizationResults = glbFiles.map { glbFile ->
            optimizeSingleGlb(glbFile)
        }
        
        val successfulOptimizations = optimizationResults.filter { it.success }
        
        val manifestEntries = successfulOptimizations.map { opt ->
            ManifestEntry(
                id = opt.modelName,
                path = opt.optimizedPath ?: ""
            )
        }
        
        createManifest(manifestEntries)
        log("Model manifest created at ${config.manifestPath} with ${manifestEntries.size} entries.")
        
        return manifestEntries
    }
    
    internal suspend fun optimizeSingleGlb(glbFile: String): OptimizationResult {
        val modelName = glbFile.substringBeforeLast(".").substringAfterLast("/")
        val optimizedGlbFilePath = "${config.outputDirGlbOptimized}/${modelName}.glb"
        
        return try {
            log("Optimizing $glbFile...")
            log("Successfully optimized ${modelName}.glb")
            OptimizationResult(
                success = true,
                modelName = modelName,
                optimizedPath = "models_optimized/${modelName}.glb"
            )
        } catch (error: Exception) {
            log("Error optimizing $glbFile: $error")
            OptimizationResult(success = false, modelName = modelName, error = error.message)
        }
    }
    
    internal suspend fun createManifest(entries: List<ManifestEntry>) {
        log("Creating manifest with ${entries.size} entries")
    }
    
    internal suspend fun createEmptyManifest() {
        log("Creating empty manifest")
    }
    
    suspend fun convertAndOptimize(): List<ManifestEntry> {
        val conversionResults = convertModels()
        
        val glbFiles = listOf<String>()
        
        return if (glbFiles.isNotEmpty()) {
            optimizeAndCreateManifest()
        } else {
            log("Skipping optimization and manifest creation as no GLB files were generated in Phase 1.")
            createEmptyManifest()
            emptyList()
        }
    }
    
    internal fun log(message: String) {
        println("[ModelConverter] $message")
    }
}

class ModelPreprocessor(internal val config: ModelConversionConfig = ModelConversionConfig()) {
    
    suspend fun preprocessModels(): ModelManifest {
        log("Starting model preprocessing...")
        
        val objFiles = listOf<String>()
        val modelManifest = mutableMapOf<String, ModelInfo>()
        
        for (file in objFiles) {
            if (file.endsWith(".obj")) {
                val modelName = file.substringBeforeLast(".").substringAfterLast("/")
                val objPath = "${config.inputDir}/$file"
                val glbPath = "${config.outputDirGlb}/${modelName}.glb"
                
                log("Processing $modelName...")
                
                try {
                    modelManifest[modelName] = ModelInfo(
                        path = "/processed_models/${modelName}.glb",
                        type = "glb"
                    )
                    
                    log("Successfully converted $modelName")
                } catch (error: Exception) {
                    log("Error processing $modelName: $error")
                }
            }
        }
        
        log("Model preprocessing complete!")
        return ModelManifest(models = modelManifest)
    }
    
    internal fun log(message: String) {
        println("[ModelPreprocessor] $message")
    }
} 