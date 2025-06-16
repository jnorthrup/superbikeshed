"use strict";
// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This bundler bake-off script is deprecated and scheduled for deletion.        #
// #   The project has standardized on Webpack as the sole bundler.                                   #
// #   This comparison is no longer relevant for active development.                                  #
// #                                                                                                  #
// ####################################################################################################
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const child_process_1 = require("child_process");
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const url_1 = require("url");
const __filename = (0, url_1.fileURLToPath)(import.meta.url);
const __dirname = path_1.default.dirname(__filename);
console.log('🥊 BUNDLER BAKE-OFF: Webpack vs Closure Compiler');
console.log('='.repeat(60));
function runCommand(command_1) {
    return __awaiter(this, arguments, void 0, function* (command, args = []) {
        return new Promise((resolve, reject) => {
            const child = (0, child_process_1.spawn)(command, args, {
                stdio: 'inherit',
                shell: true,
                cwd: __dirname
            });
            child.on('close', (code) => {
                if (code === 0) {
                    resolve();
                }
                else {
                    reject(new Error(`Command failed with exit code ${code}`));
                }
            });
            child.on('error', reject);
        });
    });
}
function loadStats(statsFile) {
    return __awaiter(this, void 0, void 0, function* () {
        try {
            const statsPath = path_1.default.join(__dirname, 'dist', statsFile);
            if (!fs_1.default.existsSync(statsPath)) {
                return null;
            }
            return JSON.parse(fs_1.default.readFileSync(statsPath, 'utf8'));
        }
        catch (error) {
            console.warn(`Warning: Could not load ${statsFile}:`, error.message);
            return null;
        }
    });
}
function formatSize(bytes) {
    if (bytes === 0)
        return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}
function formatTime(ms) {
    if (ms < 1000)
        return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
}
function runBakeOff() {
    return __awaiter(this, void 0, void 0, function* () {
        console.log('🔧 Preparing build environment...');
        // Ensure dist directory exists
        const distDir = path_1.default.join(__dirname, 'dist');
        if (!fs_1.default.existsSync(distDir)) {
            fs_1.default.mkdirSync(distDir, { recursive: true });
        }
        console.log('\n🏁 Round 1: Webpack Build');
        console.log('-'.repeat(30));
        try {
            yield runCommand('npm', ['run', 'build:webpack']);
            console.log('✅ Webpack build completed');
        }
        catch (error) {
            console.error('❌ Webpack build failed:', error.message);
        }
        console.log('\n🏁 Round 2: Closure Compiler Build');
        console.log('-'.repeat(40));
        try {
            yield runCommand('npm', ['run', 'build:closure']);
            console.log('✅ Closure Compiler build completed');
        }
        catch (error) {
            console.error('❌ Closure Compiler build failed:', error.message);
        }
        console.log('\n📊 RESULTS COMPARISON');
        console.log('='.repeat(60));
        // Load build stats
        const webpackStats = yield loadStats('webpack-stats.json');
        const closureStats = yield loadStats('closure-stats.json');
        if (!webpackStats && !closureStats) {
            console.error('❌ No build stats found. Both builds may have failed.');
            return;
        }
        // Create comparison table
        console.log('\n📈 Build Performance:');
        console.log('┌─────────────────┬──────────────┬──────────────┐');
        console.log('│ Metric          │ Webpack      │ Closure      │');
        console.log('├─────────────────┼──────────────┼──────────────┤');
        const webpackTime = (webpackStats === null || webpackStats === void 0 ? void 0 : webpackStats.buildTime) || 0;
        const closureTime = (closureStats === null || closureStats === void 0 ? void 0 : closureStats.buildTime) || 0;
        console.log(`│ Build Time      │ ${formatTime(webpackTime).padEnd(12)} │ ${formatTime(closureTime).padEnd(12)} │`);
        const webpackSize = (webpackStats === null || webpackStats === void 0 ? void 0 : webpackStats.totalSize) || 0;
        const closureSize = (closureStats === null || closureStats === void 0 ? void 0 : closureStats.totalSize) || 0;
        console.log(`│ Bundle Size     │ ${formatSize(webpackSize).padEnd(12)} │ ${formatSize(closureSize).padEnd(12)} │`);
        const webpackWarnings = (webpackStats === null || webpackStats === void 0 ? void 0 : webpackStats.warnings) || 0;
        const closureWarnings = (closureStats === null || closureStats === void 0 ? void 0 : closureStats.warnings) || 0;
        console.log(`│ Warnings        │ ${webpackWarnings.toString().padEnd(12)} │ ${closureWarnings.toString().padEnd(12)} │`);
        const webpackErrors = (webpackStats === null || webpackStats === void 0 ? void 0 : webpackStats.errors) || 0;
        const closureErrors = (closureStats === null || closureStats === void 0 ? void 0 : closureStats.errors) || 0;
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
            }
            else if (closureSize < webpackSize) {
                console.log(`📦 Closure Compiler produced a smaller bundle by ${formatSize(sizeDiff)} (${sizePercent}% smaller)`);
            }
            else {
                console.log('📦 Both bundlers produced similar sized bundles');
            }
            // Time comparison
            const timeDiff = Math.abs(webpackTime - closureTime);
            const timePercent = ((timeDiff / Math.max(webpackTime, closureTime)) * 100).toFixed(1);
            if (webpackTime < closureTime) {
                console.log(`⚡ Webpack was faster by ${formatTime(timeDiff)} (${timePercent}% faster)`);
            }
            else if (closureTime < webpackTime) {
                console.log(`⚡ Closure Compiler was faster by ${formatTime(timeDiff)} (${timePercent}% faster)`);
            }
            else {
                console.log('⚡ Both bundlers had similar build times');
            }
            // Overall winner
            console.log('\n🏆 WINNER DETERMINATION:');
            let webpackScore = 0;
            let closureScore = 0;
            if (webpackSize <= closureSize)
                webpackScore++;
            else
                closureScore++;
            if (webpackTime <= closureTime)
                webpackScore++;
            else
                closureScore++;
            if (webpackWarnings <= closureWarnings)
                webpackScore++;
            else
                closureScore++;
            if (webpackScore > closureScore) {
                console.log('🥇 Webpack wins this bake-off!');
            }
            else if (closureScore > webpackScore) {
                console.log('🥇 Closure Compiler wins this bake-off!');
            }
            else {
                console.log('🤝 It\'s a tie! Both bundlers performed equally well.');
            }
        }
        else if (webpackStats) {
            console.log('⚠️  Only Webpack build succeeded');
        }
        else if (closureStats) {
            console.log('⚠️  Only Closure Compiler build succeeded');
        }
        console.log('\n📁 Detailed stats available in:');
        console.log('   - dist/webpack-stats.json');
        console.log('   - dist/closure-stats.json');
        console.log('\n🎯 Bundle files generated:');
        console.log('   - dist/webpack-bundle.js (Webpack)');
        console.log('   - dist/closure-bundle.js (Closure Compiler)');
        console.log('\n🏁 Bake-off complete!');
    });
}
// Run the bake-off
runBakeOff().catch(error => {
    console.error('💥 Bake-off failed:', error);
    process.exit(1);
});
