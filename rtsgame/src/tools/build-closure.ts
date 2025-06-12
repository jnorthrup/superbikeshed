// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This Closure Compiler build script is deprecated and scheduled for deletion.  #
// #   The project has standardized on Webpack as the sole bundler.                                   #
// #   Please use npm run build (for production) or npm run dev (for development).                    #
// #                                                                                                  #
// ####################################################################################################

import pkg from 'google-closure-compiler';
const { compiler: ClosureCompiler } = pkg;
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('🔨 Starting Closure Compiler build...');

const startTime = Date.now();

// Create dist directory if it doesn't exist
const distDir = path.join(__dirname, 'dist');
if (!fs.existsSync(distDir)) {
  fs.mkdirSync(distDir, { recursive: true });
}

// Read closure config
const configPath = path.join(__dirname, 'closure-config.json');
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

// Prepare compiler options
const compilerOptions = {
  ...config,
  js: [
    'js_rewritten/**/*.js',
    'js/**/*.js',
    '!js/**/node_modules/**',
    '!**/test/**',
    '!**/tests/**'
  ],
  externs: [], // Add any external library definitions here
  // Override some settings for better ES module support
  module_resolution: 'NODE',
  process_common_js_modules: true,
  dependency_mode: 'PRUNE_LEGACY',
  entry_point: config.entry_point
};

console.log('📄 Configuration loaded');
console.log('🎯 Entry points:', config.entry_point.length);

const closureCompiler = new ClosureCompiler(compilerOptions);

closureCompiler.run((exitCode, stdOut, stdErr) => {
  const buildTime = Date.now() - startTime;
  
  if (exitCode !== 0) {
    console.error('❌ Closure Compiler build failed!');
    console.error('Exit code:', exitCode);
    if (stdErr) {
      console.error('Errors:', stdErr);
    }
    process.exit(1);
  }

  console.log('✅ Closure Compiler build completed successfully!');
  console.log(`⏱️  Build time: ${buildTime}ms`);

  // Get bundle information
  const outputFile = config.js_output_file;
  const outputPath = path.resolve(__dirname, outputFile);
  
  let bundleSize = 0;
  if (fs.existsSync(outputPath)) {
    const stats = fs.statSync(outputPath);
    bundleSize = stats.size;
    console.log(`📦 Bundle size: ${(bundleSize / 1024).toFixed(2)} KB`);
  }

  // Parse any warnings/errors from stdout
  const warnings = [];
  const errors = [];
  
  if (stdOut) {
    const lines = stdOut.split('\n');
    lines.forEach(line => {
      if (line.includes('WARNING')) {
        warnings.push(line);
      } else if (line.includes('ERROR')) {
        errors.push(line);
      }
    });
  }

  // Save build stats
  const closureStats = {
    bundler: 'closure-compiler',
    buildTime,
    assets: [{
      name: 'closure-bundle.js',
      size: bundleSize,
      sizeKB: (bundleSize / 1024).toFixed(2)
    }],
    totalSize: bundleSize,
    warnings: warnings.length,
    errors: errors.length,
    warningDetails: warnings,
    errorDetails: errors,
    compilationLevel: config.compilation_level,
    languageIn: config.language_in,
    languageOut: config.language_out
  };

  fs.writeFileSync(
    path.join(__dirname, 'dist', 'closure-stats.json'),
    JSON.stringify(closureStats, null, 2)
  );

  console.log('📊 Build stats saved to dist/closure-stats.json');

  // Show warnings if any
  if (warnings.length > 0) {
    console.warn(`⚠️  ${warnings.length} warning(s)`);
    if (warnings.length <= 5) {
      warnings.forEach(warning => console.warn(warning));
    } else {
      console.warn('Too many warnings to display. Check closure-stats.json for details.');
    }
  }

  // Show errors if any (shouldn't happen if build succeeded)
  if (errors.length > 0) {
    console.error(`🚨 ${errors.length} error(s):`);
    errors.forEach(error => console.error(error));
  }

  console.log('🎉 Closure Compiler build process complete!');
});