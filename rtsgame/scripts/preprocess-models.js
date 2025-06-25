import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const MODELS_DIR = path.join(__dirname, '../models');
const PROCESSED_DIR = path.join(__dirname, '../processed_models');
const MANIFEST_PATH = path.join(__dirname, '../public/assets/model-manifest.json');

async function preprocessModels() {
    console.log('Starting model preprocessing...');
    
    // Ensure directories exist
    await fs.ensureDir(PROCESSED_DIR);
    await fs.ensureDir(path.dirname(MANIFEST_PATH));
    
    // Get all OBJ files
    const objFiles = await fs.readdir(MODELS_DIR);
    const modelManifest = {};
    
    for (const file of objFiles) {
        if (path.extname(file) === '.obj') {
            const modelName = path.basename(file, '.obj');
            const objPath = path.join(MODELS_DIR, file);
            const glbPath = path.join(PROCESSED_DIR, `${modelName}.glb`);
            
            console.log(`Processing ${modelName}...`);
            
            try {
                // Convert OBJ to GLB using obj2gltf
                execSync(`obj2gltf -i "${objPath}" -o "${glbPath}"`);
                
                // Add to manifest
                modelManifest[modelName] = {
                    path: `/processed_models/${modelName}.glb`,
                    type: 'glb'
                };
                
                console.log(`Successfully converted ${modelName}`);
            } catch (error) {
                console.error(`Error processing ${modelName}:`, error);
            }
        }
    }
    
    // Write manifest
    await fs.writeJson(MANIFEST_PATH, modelManifest, { spaces: 2 });
    console.log('Model preprocessing complete!');
}

preprocessModels().catch(console.error); 