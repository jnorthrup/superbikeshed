#!/usr/bin/env kotlin

@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.17.1")

import com.google.gson.Gson
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun main() {
    println("Testing JSON libraries...")
    
    val testJson = """{"name": "test", "value": 42, "active": true}"""
    
    // Test Gson
    try {
        val gson = Gson()
        val result = gson.fromJson(testJson, Map::class.java)
        println("✓ Gson: $result")
    } catch (e: Exception) {
        println("✗ Gson failed: ${e.message}")
    }
    
    // Test Jackson
    try {
        val mapper = ObjectMapper().registerKotlinModule()
        val result = mapper.readValue(testJson, Map::class.java)
        println("✓ Jackson: $result")
    } catch (e: Exception) {
        println("✗ Jackson failed: ${e.message}")
    }
    
    // Test TrikeShed Simple
    try {
        val result = testJson.simpleJson().properties()
        println("✓ TrikeShed Simple: $result")
    } catch (e: Exception) {
        println("✗ TrikeShed Simple failed: ${e.message}")
    }
    
    // Test TrikeShed Compact
    try {
        val result = testJson.json().properties()
        println("✓ TrikeShed Compact: $result")
    } catch (e: Exception) {
        println("✗ TrikeShed Compact failed: ${e.message}")
    }
    
    // Test TrikeShed Fast
    try {
        val result = testJson.fastJson().properties()
        println("✓ TrikeShed Fast: $result")
    } catch (e: Exception) {
        println("✗ TrikeShed Fast failed: ${e.message}")
    }
    
    // Test JsonBBCursive
    try {
        val result = borg.trikeshed.ljson.JsonBBCursive.parse(testJson)
        println("✓ JsonBBCursive: ${result.element}")
    } catch (e: Exception) {
        println("✗ JsonBBCursive failed: ${e.message}")
    }
    
    println("Test completed!")
} 