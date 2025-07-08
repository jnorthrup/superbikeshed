#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.slf4j:slf4j-simple:2.0.9")

import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("HelloWorld")

logger.info("🚀 Hello World from k2script!")
logger.info("📦 Successfully loaded slf4j-simple dependency")
logger.info("🔧 Running with Kotlin 2 scripting API")

println("Hello World - Logging to console via slf4j-simple")
println("Check the logs above for structured output") 