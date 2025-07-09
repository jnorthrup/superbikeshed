package buildtools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Plugin that enforces build policies:
 * 1. Build file immutability
 * 2. Version stripping from child projects
 * 3. Project armor application
 */
class BuildPolicyPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Only apply to root project
        if (project != project.rootProject) {
            return
        }
        
        // Register version stripping task
        project.tasks.register("stripVersions") {
            group = "build policy"
            description = "Strip version declarations from child project build files"
            
            doLast {
                GradleVersionStripper.processProject(project.rootDir, dryRun = false)
            }
        }
        
        // Register dry-run version stripping task
        project.tasks.register("stripVersionsDryRun") {
            group = "build policy"
            description = "Preview version stripping without making changes"
            
            doLast {
                GradleVersionStripper.processProject(project.rootDir, dryRun = true)
            }
        }
        
        // Register build file validation task
        project.tasks.register("validateBuildFiles") {
            group = "build policy"
            description = "Validate build file immutability"
            
            doLast {
                val results = BuildFileImmutabilityChecker.validateProject(project.rootDir)
                
                val unauthorized = results.filterIsInstance<BuildFileImmutabilityChecker.ValidationResult.UnauthorizedChange>()
                if (unauthorized.isNotEmpty()) {
                    unauthorized.forEach { 
                        println(it.toErrorMessage())
                    }
                    throw RuntimeException("Build file immutability violations detected!")
                }
                
                println("All build files validated successfully")
                results.forEach { result ->
                    when (result) {
                        is BuildFileImmutabilityChecker.ValidationResult.NewFile -> 
                            println("New file: ${result.path}")
                        is BuildFileImmutabilityChecker.ValidationResult.Unchanged -> 
                            println("Unchanged: ${result.path}")
                        is BuildFileImmutabilityChecker.ValidationResult.AuthorizedChange -> 
                            println("Authorized change: ${result.path} (${result.permission.purpose})")
                        else -> { /* Already handled */ }
                    }
                }
            }
        }
        
        // Register permission granting task
        project.tasks.register("grantBuildFilePermission") {
            group = "build policy"
            description = "Grant permission to modify a build file"
            
            doLast {
                val file = project.findProperty("file")?.toString()
                    ?: throw IllegalArgumentException("Must specify -Pfile=<path>")
                val purpose = project.findProperty("purpose")?.toString()
                    ?: throw IllegalArgumentException("Must specify -Ppurpose=<reason>")
                val duration = project.findProperty("duration")?.toString()?.toLongOrNull()
                
                BuildFileImmutabilityChecker.grantPermission(file, purpose, duration)
            }
        }
        
        // Register project armor task
        project.tasks.register("applyProjectArmor") {
            group = "build policy"
            description = "Apply Project Armor suppressions and opt-ins to all Kotlin files"
            
            doLast {
                ProjectArmorStacktraceFixer.applyArmorToProject(project.rootDir)
            }
        }
        
        // Register stacktrace processing task
        project.tasks.register("processStackTrace") {
            group = "build policy"
            description = "Process a stacktrace with Project Armor"
            
            doLast {
                val stacktraceFile = project.findProperty("stacktrace")?.toString()?.let { File(it) }
                    ?: throw IllegalArgumentException("Must specify -Pstacktrace=<file>")
                
                if (!stacktraceFile.exists()) {
                    throw IllegalArgumentException("Stacktrace file not found: ${stacktraceFile.path}")
                }
                
                val processed = ProjectArmorStacktraceFixer.processStackTrace(
                    stacktraceFile.readText(),
                    project.rootDir
                )
                
                val outputFile = File(stacktraceFile.parentFile, stacktraceFile.nameWithoutExtension + "_processed.txt")
                outputFile.writeText(processed)
                
                println("Processed stacktrace written to: ${outputFile.path}")
            }
        }
        
        // Register Opus-optimal stacktrace processing task
        project.tasks.register("processStackTraceOpus") {
            group = "build policy"
            description = "Process a stacktrace with Opus-optimal format"
            
            doLast {
                val stacktraceFile = project.findProperty("stacktrace")?.toString()?.let { File(it) }
                    ?: throw IllegalArgumentException("Must specify -Pstacktrace=<file>")
                
                if (!stacktraceFile.exists()) {
                    throw IllegalArgumentException("Stacktrace file not found: ${stacktraceFile.path}")
                }
                
                val gradleLog = project.findProperty("gradleLog")?.toString()?.let { File(it) }?.readText()
                
                val processed = OpusOptimalStacktraceFixer.processStackTraceForLLM(
                    stacktraceFile.readText(),
                    project.rootDir,
                    gradleLog
                )
                
                val outputFile = File(stacktraceFile.parentFile, stacktraceFile.nameWithoutExtension + "_processed.txt")
                outputFile.writeText(processed)
                
                println("Processed stacktrace (Opus format) written to: ${outputFile.path}")
            }
        }
        
        // Register infix lambda fixing task
        project.tasks.register("fixInfixLambdas") {
            group = "build policy"
            description = "Fix infix lambda type annotations"
            
            doLast {
                InfixLambdaTypeFixer.processProject(project.rootDir)
            }
        }
        
        // Hook into build lifecycle
        project.afterEvaluate {
            // Run version stripping before dependency resolution
            project.tasks.findByName("preBuild")?.dependsOn("stripVersions")
            
            // Validate build files before configuration
            if (project.hasProperty("enforceBuildImmutability")) {
                project.tasks.findByName("build")?.dependsOn("validateBuildFiles")
            }
        }
        
        // Create pre-build hook for Ben Manes plugin integration
        project.tasks.register("preBuildPolicy") {
            group = "build policy"
            description = "Run all pre-build policy checks"
            
            dependsOn("stripVersions")
            
            if (project.hasProperty("enforceBuildImmutability")) {
                dependsOn("validateBuildFiles")
            }
            
            doLast {
                println("Pre-build policies applied successfully")
            }
        }
    }
}