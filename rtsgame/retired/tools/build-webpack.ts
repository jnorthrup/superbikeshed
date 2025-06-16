import webpack from 'webpack';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Import webpack config
const webpackConfig = await import('./webpack.config.js').then(module => module.default);

console.log('🔨 Starting Webpack build...');
console.log('Entry points:', Object.keys(webpackConfig.entry));

const startTime = Date.now();

const compiler = webpack(webpackConfig);

compiler.run((err, stats) => {
  const buildTime = Date.now() - startTime;
  
  if (err || stats.hasErrors()) {
    console.error('❌ Webpack build failed!');
    if (err) {
      console.error('Compilation error:', err);
    }
    if (stats && stats.hasErrors()) {
      console.error('Stats errors:', stats.toJson().errors);
    }
    process.exit(1);
  }

  const info = stats.toJson();
  
  console.log('✅ Webpack build completed successfully!');
  console.log(`⏱️  Build time: ${buildTime}ms`);
  
  // Get bundle information
  const assets = info.assets || [];
  const mainAsset = assets.find(asset => asset.name === 'game-bundle.js');
  
  if (mainAsset) {
    console.log(`📦 Bundle size: ${(mainAsset.size / 1024).toFixed(2)} KB`);
  }
  
  // Save build stats
  const webpackStats = {
    bundler: 'webpack',
    buildTime,
    assets: assets.map(asset => ({
      name: asset.name,
      size: asset.size,
      sizeKB: (asset.size / 1024).toFixed(2)
    })),
    totalSize: assets.reduce((sum, asset) => sum + asset.size, 0),
    warnings: info.warnings?.length || 0,
    errors: info.errors?.length || 0
  };
  
  fs.writeFileSync(
    path.join(__dirname, 'dist', 'webpack-stats.json'),
    JSON.stringify(webpackStats, null, 2)
  );
  
  console.log('📊 Build stats saved to dist/webpack-stats.json');
  
  // Show warnings if any
  if (info.warnings && info.warnings.length > 0) {
    console.warn(`⚠️  ${info.warnings.length} warning(s):`);
    info.warnings.forEach(warning => console.warn(warning));
  }
  
  compiler.close((closeErr) => {
    if (closeErr) {
      console.error('Error closing compiler:', closeErr);
    }
  });
});