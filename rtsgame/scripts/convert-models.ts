import fs from 'fs-extra';
import glob from 'glob';
import path from 'path';
import obj2gltf from 'obj2gltf';
import gltfPipeline from 'gltf-pipeline';

interface ConversionResult {
  success: boolean;
  modelName: string;
  objFile: string;
  glbFilePath?: string;
  error?: Error;
}

interface OptimizationResult {
  success: boolean;
  modelName: string;
  optimizedPath?: string;
  error?: Error;
}

interface ManifestEntry {
  id: string;
  path: string;
}

const inputDir = path.resolve(__dirname, '../models');
const outputDirGlb = path.resolve(__dirname, '../processed_models/glb');
const outputDirGlbOptimized = path.resolve(__dirname, '../processed_models/glb_optimized');
const manifestDir = path.resolve(__dirname, '../public/assets');
const manifestPath = path.join(manifestDir, 'model-manifest.json');

// Ensure output directories exist
fs.ensureDirSync(outputDirGlb);
fs.ensureDirSync(outputDirGlbOptimized);
fs.ensureDirSync(manifestDir);

async function convertModels(): Promise<ConversionResult[]> {
  console.log(`Looking for OBJ models in: ${inputDir}`);
  const objFiles = glob.sync(path.join(inputDir, '**/*.obj'));

  if (objFiles.length === 0) {
    console.warn(`No .obj files found in ${inputDir}. Please ensure models are present and path is correct.`);
    // Create an empty manifest if no models are found, to prevent later build steps from failing
    await fs.writeJson(manifestPath, [], { spaces: 2 });
    console.log(`Empty model manifest created at ${manifestPath}`);
    return [];
  }

  console.log(`Found ${objFiles.length} OBJ files. Starting conversion to GLB...`);
  const conversionPromises = objFiles.map(async (objFile: string): Promise<ConversionResult> => {
    const modelName = path.basename(objFile, '.obj');
    const glbFilePath = path.join(outputDirGlb, `${modelName}.glb`);

    try {
      console.log(`Converting ${objFile} to GLB...`);
      const glb = await obj2gltf(objFile, { binary: true });
      await fs.writeFile(glbFilePath, glb);
      console.log(`Successfully converted ${modelName}.obj to ${modelName}.glb`);
      return { success: true, modelName, objFile, glbFilePath };
    } catch (error) {
      console.error(`Error converting ${objFile}:`, error);
      return { success: false, modelName, objFile, error: error as Error };
    }
  });

  const conversionResults = await Promise.all(conversionPromises);
  const successfulConversions = conversionResults.filter(r => r.success);

  if (successfulConversions.length === 0) {
    console.error("No models were successfully converted. Skipping optimization and manifest generation.");
    // Create an empty manifest
    await fs.writeJson(manifestPath, [], { spaces: 2 });
    console.log(`Empty model manifest created at ${manifestPath}`);
    return conversionResults;
  }

  console.log(`\nSuccessfully converted ${successfulConversions.length} models to GLB.`);
  return conversionResults;
}

async function optimizeAndCreateManifest(): Promise<void> {
  console.log("\nStarting optimization and manifest creation (Phase 2)...");

  const glbFiles = glob.sync(path.join(outputDirGlb, '**/*.glb'));
  if (glbFiles.length === 0) {
    console.warn("No .glb files found for optimization.");
    await fs.writeJson(manifestPath, [], { spaces: 2 });
    console.log(`Empty model manifest created at ${manifestPath}`);
    return;
  }

  const optimizationPromises = glbFiles.map(async (glbFile: string): Promise<OptimizationResult> => {
    const modelName = path.basename(glbFile, '.glb');
    const optimizedGlbFilePath = path.join(outputDirGlbOptimized, `${modelName}.glb`);
    try {
      console.log(`Optimizing ${glbFile}...`);
      const glb = await fs.readFile(glbFile);
      // Enable Draco compression
      const options = {
        dracoOptions: {
          compressionLevel: 7 // Default is 7 if Draco is used. Explicitly setting.
        },
        resourceDirectory: path.dirname(glbFile) // Important for external textures if any
      };
      const results = await gltfPipeline.processGlb(glb, options);
      await fs.writeFile(optimizedGlbFilePath, results.glb);
      console.log(`Successfully optimized ${modelName}.glb`);
      return { success: true, modelName, optimizedPath: `models_optimized/${modelName}.glb` };
    } catch (error) {
      console.error(`Error optimizing ${glbFile}:`, error);
      return { success: false, modelName, error: error as Error };
    }
  });

  const optimizationResults = await Promise.all(optimizationPromises);
  const successfulOptimizations = optimizationResults.filter(r => r.success);

  const manifestEntries: ManifestEntry[] = successfulOptimizations.map(opt => ({
    id: opt.modelName,
    path: opt.optimizedPath!
  }));

  await fs.writeJson(manifestPath, manifestEntries, { spaces: 2 });
  console.log(`Model manifest created at ${manifestPath} with ${manifestEntries.length} entries.`);
}

async function main(): Promise<void> {
  const conversionResults = await convertModels(); // Phase 1
  // Check if there were successful conversions before attempting optimization
  const glbFiles = glob.sync(path.join(outputDirGlb, '**/*.glb'));
  if (glbFiles.length > 0) {
    await optimizeAndCreateManifest(); // Phase 2
  } else {
    console.log("Skipping optimization and manifest creation as no GLB files were generated in Phase 1.");
    // Ensure an empty manifest is still created if it wasn't already by convertModels
    if (!fs.existsSync(manifestPath)) {
        await fs.writeJson(manifestPath, [], { spaces: 2 });
        console.log(`Empty model manifest created at ${manifestPath} as a fallback.`);
    }
  }
}

main().catch(error => {
  console.error("An error occurred during the model processing script:", error);
  process.exit(1);
}); 