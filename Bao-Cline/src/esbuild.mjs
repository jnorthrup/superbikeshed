import * as esbuild from "esbuild"
import * as fs from "fs"
import * as path from "path"
import { fileURLToPath } from "url"
import process from "node:process"
import * as console from "node:console"

// Build utilities inlined
function copyPaths(paths, srcDir, buildDir) {
	paths.forEach(([src, dest, options = {}]) => {
		const srcPath = path.resolve(srcDir, src)
		const destPath = path.resolve(buildDir, dest)
		
		if (options.optional && !fs.existsSync(srcPath)) return
		
		if (fs.existsSync(srcPath)) {
			const destDir = path.dirname(destPath)
			if (!fs.existsSync(destDir)) {
				fs.mkdirSync(destDir, { recursive: true })
			}
			
			if (fs.lstatSync(srcPath).isDirectory()) {
				if (fs.existsSync(destPath)) {
					fs.rmSync(destPath, { recursive: true, force: true })
				}
				fs.mkdirSync(destPath, { recursive: true })
				const files = fs.readdirSync(srcPath)
				files.forEach(file => {
					const srcFile = path.join(srcPath, file)
					const destFile = path.join(destPath, file)
					if (fs.lstatSync(srcFile).isDirectory()) {
						copyPaths([[file, file]], srcPath, destPath)
					} else {
						fs.copyFileSync(srcFile, destFile)
					}
				})
			} else {
				fs.copyFileSync(srcPath, destPath)
			}
		}
	})
}

function copyWasms(srcDir, distDir) {
	// Copy WASM files if they exist
	const wasmSrc = path.join(srcDir, "node_modules/tree-sitter-wasms")
	const wasmDest = path.join(distDir, "tree-sitter-wasms")
	if (fs.existsSync(wasmSrc) && fs.lstatSync(wasmSrc).isDirectory()) {
		copyPaths([["node_modules/tree-sitter-wasms", "tree-sitter-wasms"]], srcDir, distDir)
	}
}

function copyLocales(srcDir, distDir) {
	// Copy locale files
	const localesPattern = "package.nls.*.json"
	const files = fs.readdirSync(srcDir).filter(f => f.match(/package\.nls\.[^.]+\.json$/))
	files.forEach(file => {
		fs.copyFileSync(path.join(srcDir, file), path.join(distDir, file))
	})
}

function setupLocaleWatcher(srcDir, distDir) {
	// Simple watcher for locale files - could be enhanced
	console.log("Locale watcher not implemented for simplified build")
}

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

async function main() {
	const name = "extension"
	const production = process.argv.includes("--production")
	const watch = process.argv.includes("--watch")
	const minify = production
	const sourcemap = !production

	/**
	 * @type {import('esbuild').BuildOptions}
	 */
	const buildOptions = {
		bundle: true,
		minify,
		sourcemap,
		logLevel: "silent",
		format: "cjs",
		sourcesContent: false,
		platform: "node",
	}

	const srcDir = __dirname
	const buildDir = __dirname
	const distDir = path.join(buildDir, "dist")

	if (fs.existsSync(distDir)) {
		console.log(`[${name}] Cleaning dist directory: ${distDir}`)
		fs.rmSync(distDir, { recursive: true, force: true })
	}

	/**
	 * @type {import('esbuild').Plugin[]}
	 */
	const plugins = [
		{
			name: "copyFiles",
			setup(build) {
				build.onEnd(() => {
					copyPaths(
						[
							["../README.md", "README.md"],
							["../CHANGELOG.md", "CHANGELOG.md"],
							["../LICENSE", "LICENSE"],
							["../.env", ".env", { optional: true }],
							["node_modules/vscode-material-icons/generated", "assets/vscode-material-icons"],
							["../webview-ui/audio", "webview-ui/audio"],
						],
						srcDir,
						buildDir,
					)
				})
			},
		},
		{
			name: "copyWasms",
			setup(build) {
				build.onEnd(() => copyWasms(srcDir, distDir))
			},
		},
		{
			name: "copyLocales",
			setup(build) {
				build.onEnd(() => copyLocales(srcDir, distDir))
			},
		},
		{
			name: "esbuild-problem-matcher",
			setup(build) {
				build.onStart(() => console.log("[esbuild-problem-matcher#onStart]"))
				build.onEnd((result) => {
					result.errors.forEach(({ text, location }) => {
						console.error(`✘ [ERROR] ${text}`)
						console.error(`    ${location.file}:${location.line}:${location.column}:`)
					})

					console.log("[esbuild-problem-matcher#onEnd]")
				})
			},
		},
	]

	/**
	 * @type {import('esbuild').BuildOptions}
	 */
	const extensionConfig = {
		...buildOptions,
		plugins,
		entryPoints: ["extension.ts"],
		outfile: "dist/extension.js",
		external: ["vscode"],
	}

	/**
	 * @type {import('esbuild').BuildOptions}
	 */
	const workerConfig = {
		...buildOptions,
		entryPoints: ["workers/countTokens.ts"],
		outdir: "dist/workers",
	}

	const [extensionCtx, workerCtx] = await Promise.all([
		esbuild.context(extensionConfig),
		esbuild.context(workerConfig),
	])

	if (watch) {
		await Promise.all([extensionCtx.watch(), workerCtx.watch()])
		copyLocales(srcDir, distDir)
		setupLocaleWatcher(srcDir, distDir)
	} else {
		await Promise.all([extensionCtx.rebuild(), workerCtx.rebuild()])
		await Promise.all([extensionCtx.dispose(), workerCtx.dispose()])
	}
}

main().catch((e) => {
	console.error(e)
	process.exit(1)
})
