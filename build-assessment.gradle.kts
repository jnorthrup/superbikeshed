// RequestFactory & CRDT Splashover Assessment Build Configuration
// This file configures the dedicated assessment build for RequestFactory server functionality
// and potential CRDT splashover effects in the TrikeShed ecosystem.

import org.gradle.api.tasks.testing.logging.TestLogEvent

// Assessment build configuration
val assessmentConfig = mapOf(
    "requestFactoryComponents" to listOf("trikeshed-services", "trikeshed-net", "k2script"),
    "crdtComponents" to listOf("trikeshed-services", "rtsgame", "trikeshed-dht"),
    "performanceThresholds" to mapOf(
        "requestThroughput" to 1000, // requests/second
        "responseLatency" to 10, // milliseconds
        "memoryUsage" to 100, // MB
        "conflictDetectionAccuracy" to 95.0, // percentage
        "splashoverContainment" to 5000 // milliseconds
    )
)

// Configure all projects for assessment
allprojects {
    // Enable test logging for assessment
    tasks.withType<Test> {
        testLogging {
            events = setOf(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR
            )
            
            // Show test results for assessment
            showExceptions = true
            showCauses = true
            showStackTraces = true
            
            // Log performance metrics
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        
        // Set timeout for assessment tests
        timeout.set(java.time.Duration.ofMinutes(10))
        
        // Enable parallel execution for performance testing
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    }
}

// Assessment-specific tasks
tasks.register("assessmentBuild") {
    group = "assessment"
    description = "Run comprehensive RequestFactory and CRDT assessment build"
    
    dependsOn(":trikeshed-services:build", ":trikeshed-net:build", ":k2script:build")
    
    doLast {
        println("✅ Assessment build configuration loaded")
        println("📊 Performance thresholds:")
        assessmentConfig["performanceThresholds"]?.forEach { (metric, threshold) ->
            println("   - $metric: $threshold")
        }
    }
}

tasks.register("runRequestFactoryAssessment") {
    group = "assessment"
    description = "Run RequestFactory server functionality assessment"
    
    dependsOn(":trikeshed-services:test")
    
    doLast {
        println("🔍 RequestFactory assessment completed")
        println("📋 Check build/reports/tests/ for detailed results")
    }
}

tasks.register("runCRDTSplashoverAssessment") {
    group = "assessment"
    description = "Run CRDT splashover detection and assessment"
    
    dependsOn(":trikeshed-services:test")
    
    doLast {
        println("🔍 CRDT splashover assessment completed")
        println("📋 Check build/reports/tests/ for conflict analysis")
    }
}

tasks.register("generateAssessmentReport") {
    group = "assessment"
    description = "Generate comprehensive assessment report"
    
    dependsOn(":trikeshed-services:jacocoTestReport")
    
    doLast {
        val reportDir = file("build/reports/assessment")
        reportDir.mkdirs()
        
        val reportFile = reportDir.resolve("build-assessment-report.md")
        reportFile.writeText("""
            # Build Assessment Report
            
            ## Configuration
            - **Build Date**: ${java.time.LocalDateTime.now()}
            - **Components**: ${assessmentConfig["requestFactoryComponents"]?.joinToString(", ")}
            - **CRDT Components**: ${assessmentConfig["crdtComponents"]?.joinToString(", ")}
            
            ## Performance Thresholds
            ${assessmentConfig["performanceThresholds"]?.map { (k, v) -> "- **$k**: $v" }?.joinToString("\n")}
            
            ## Next Steps
            1. Run assessment tests
            2. Review performance metrics
            3. Address any failures
            4. Optimize based on results
        """.trimIndent())
        
        println("📊 Assessment report generated: $reportFile")
    }
}

// Configure specific projects for assessment
project(":trikeshed-services") {
    tasks.withType<Test> {
        // Add assessment-specific test filters
        filter {
            includeTestsMatching("*AssessmentTest*")
            includeTestsMatching("*CRDT*")
            includeTestsMatching("*RequestFactory*")
        }
    }
}

project(":rtsgame") {
    tasks.withType<Test> {
        filter {
            includeTestsMatching("*Conflict*")
            includeTestsMatching("*Command*")
        }
    }
}

project(":trikeshed-dht") {
    tasks.withType<Test> {
        filter {
            includeTestsMatching("*Routing*")
            includeTestsMatching("*Conflict*")
        }
    }
}

// Assessment completion task
tasks.register("completeAssessment") {
    group = "assessment"
    description = "Complete full assessment and generate final report"
    
    dependsOn(
        "assessmentBuild",
        "runRequestFactoryAssessment", 
        "runCRDTSplashoverAssessment",
        "generateAssessmentReport"
    )
    
    doLast {
        println("🎯 Assessment build completed successfully!")
        println("📊 Reports available in build/reports/")
        println("📋 Run './scripts/run-requestfactory-assessment.sh' for detailed assessment")
    }
} 