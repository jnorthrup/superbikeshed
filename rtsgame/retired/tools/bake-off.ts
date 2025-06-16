// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This bundler bake-off script is deprecated and scheduled for deletion.        #
// #   The project has standardized on Webpack as the sole bundler.                                   #
// #   This comparison is no longer relevant for active development.                                  #
// #                                                                                                  #
// ####################################################################################################

import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log('🥊 BUNDLER BAKE-OFF: Webpack vs Closure Compiler');
console.log('=' .repeat(60));

async function runCommand(command, args = []) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { 
      stdio: 'inherit',
      shell: true,
      cwd: __dirname
    });
    
    child.on('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`Command failed with exit code ${code}`));
      }
    });
    
    child.on('error', reject);
  });
}

async function loadStats(statsFile) {
  try {
    const statsPath = path.join(__dirname, 'dist', statsFile);
    if (!fs.existsSync(statsPath)) {
      return null;
    }
    return JSON.parse(fs.readFileSync(statsPath, 'utf8'));
  } catch (error) {
    console.warn(`Warning: Could not load ${statsFile}:`, error.message);
    return null;
  }
}

function formatSize(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function formatTime(ms) {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

async function runBakeOff() {
  console.log('🔧 Preparing build environment...');
  
  // Ensure dist directory exists
  const distDir = path.join(__dirname, 'dist');
  if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
  }

  console.log('\n🏁 Round 1: Webpack Build');
  console.log('-'.repeat(30));
  
  try {
    await runCommand('npm', ['run', 'build:webpack']);
    console.log('✅ Webpack build completed');
  } catch (error) {
    console.error('❌ Webpack build failed:', error.message);
  }

  console.log('\n🏁 Round 2: Closure Compiler Build');
  console.log('-'.repeat(40));
  
  try {
    await runCommand('npm', ['run', 'build:closure']);
    console.log('✅ Closure Compiler build completed');
  } catch (error) {
    console.error('❌ Closure Compiler build failed:', error.message);
  }

  console.log('\n📊 RESULTS COMPARISON');
  console.log('=' .repeat(60));

  // Load build stats
  const webpackStats = await loadStats('webpack-stats.json');
  const closureStats = await loadStats('closure-stats.json');

  if (!webpackStats && !closureStats) {
    console.error('❌ No build stats found. Both builds may have failed.');
    return;
  }

  // Create comparison table
  console.log('\n📈 Build Performance:');
  console.log('┌─────────────────┬──────────────┬──────────────┐');
  console.log('│ Metric          │ Webpack      │ Closure      │');
  console.log('├─────────────────┼──────────────┼──────────────┤');
  
  const webpackTime = webpackStats?.buildTime || 0;
  const closureTime = closureStats?.buildTime || 0;
  console.log(`│ Build Time      │ ${formatTime(webpackTime).padEnd(12)} │ ${formatTime(closureTime).padEnd(12)} │`);
  
  const webpackSize = webpackStats?.totalSize || 0;
  const closureSize = closureStats?.totalSize || 0;
  console.log(`│ Bundle Size     │ ${formatSize(webpackSize).padEnd(12)} │ ${formatSize(closureSize).padEnd(12)} │`);
  
  const webpackWarnings = webpackStats?.warnings || 0;
  const closureWarnings = closureStats?.warnings || 0;
  console.log(`│ Warnings        │ ${webpackWarnings.toString().padEnd(12)} │ ${closureWarnings.toString().padEnd(12)} │`);
  
  const webpackErrors = webpackStats?.errors || 0;
  const closureErrors = closureStats?.errors || 0;
  console.log(`│ Errors          │ ${webpackErrors.toString().padEnd(12)} │ ${closureErrors.toString().padEnd(12)} │`);
  
  console.log('└─────────────────┴──────────────┴──────────────┘');

  // Analysis
  console.log('\n🔍 ANALYSIS:');
  
  if (webpackStats && closureStats) {
    // Size comparison
    const sizeDiff = Math.abs(webpackSize - closureSize);
    const sizePercent = ((sizeDiff / Math.max(webpackSize, closureSize)) * 100).toFixed(1);
    
    if (webpackSize < closureSize) {
      console.log(`📦 Webpack produced a smaller bundle by ${formatSize(sizeDiff)} (${sizePercent}% smaller)`);
    } else if (closureSize < webpackSize) {
      console.log(`📦 Closure Compiler produced a smaller bundle by ${formatSize(sizeDiff)} (${sizePercent}% smaller)`);
    } else {
      console.log('📦 Both bundlers produced similar sized bundles');
    }
    
    // Time comparison
    const timeDiff = Math.abs(webpackTime - closureTime);
    const timePercent = ((timeDiff / Math.max(webpackTime, closureTime)) * 100).toFixed(1);
    
    if (webpackTime < closureTime) {
      console.log(`⚡ Webpack was faster by ${formatTime(timeDiff)} (${timePercent}% faster)`);
    } else if (closureTime < webpackTime) {
      console.log(`⚡ Closure Compiler was faster by ${formatTime(timeDiff)} (${timePercent}% faster)`);
    } else {
      console.log('⚡ Both bundlers had similar build times');
    }
    
    // Overall winner
    console.log('\n🏆 WINNER DETERMINATION:');
    let webpackScore = 0;
    let closureScore = 0;
    
    if (webpackSize <= closureSize) webpackScore++;
    else closureScore++;
    
    if (webpackTime <= closureTime) webpackScore++;
    else closureScore++;
    
    if (webpackWarnings <= closureWarnings) webpackScore++;
    else closureScore++;
    
    if (webpackScore > closureScore) {
      console.log('🥇 Webpack wins this bake-off!');
    } else if (closureScore > webpackScore) {
      console.log('🥇 Closure Compiler wins this bake-off!');
    } else {
      console.log('🤝 It\'s a tie! Both bundlers performed equally well.');
    }
    
  } else if (webpackStats) {
    console.log('⚠️  Only Webpack build succeeded');
  } else if (closureStats) {
    console.log('⚠️  Only Closure Compiler build succeeded');
  }

  console.log('\n📁 Detailed stats available in:');
  console.log('   - dist/webpack-stats.json');
  console.log('   - dist/closure-stats.json');
  
  console.log('\n🎯 Bundle files generated:');
  console.log('   - dist/webpack-bundle.js (Webpack)');
  console.log('   - dist/closure-bundle.js (Closure Compiler)');
  
  console.log('\n🏁 Bake-off complete!');
}

// Run the bake-off
runBakeOff().catch(error => {
  console.error('💥 Bake-off failed:', error);
  process.exit(1);
});
