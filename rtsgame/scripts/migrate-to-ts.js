const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Configuration
const JS_DIR = path.join(__dirname, '../js');
const SRC_DIR = path.join(__dirname, '../src');
const EXCLUDED_DIRS = ['node_modules', 'dist', 'build'];

// Create src directory if it doesn't exist
if (!fs.existsSync(SRC_DIR)) {
    fs.mkdirSync(SRC_DIR, { recursive: true });
}

// Function to convert JS file to TS
function convertJsToTs(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const relativePath = path.relative(JS_DIR, filePath);
    const newPath = path.join(SRC_DIR, relativePath.replace('.js', '.ts'));
    
    // Create directory if it doesn't exist
    const dir = path.dirname(newPath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    
    // Write the file with .ts extension
    fs.writeFileSync(newPath, content);
    console.log(`Converted: ${relativePath} -> ${path.relative(SRC_DIR, newPath)}`);
}

// Function to process directory
function processDirectory(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        
        if (entry.isDirectory()) {
            if (!EXCLUDED_DIRS.includes(entry.name)) {
                processDirectory(fullPath);
            }
        } else if (entry.isFile() && entry.name.endsWith('.js')) {
            convertJsToTs(fullPath);
        }
    }
}

// Main execution
console.log('Starting migration to TypeScript...');
processDirectory(JS_DIR);
console.log('Migration complete!');

// Update tsconfig.json paths
const tsconfigPath = path.join(__dirname, '../tsconfig.json');
const tsconfig = JSON.parse(fs.readFileSync(tsconfigPath, 'utf8'));
tsconfig.include = ['src/**/*'];
tsconfig.exclude = ['node_modules', '**/*.spec.ts', 'js'];
fs.writeFileSync(tsconfigPath, JSON.stringify(tsconfig, null, 2));

console.log('Updated tsconfig.json'); 