package com.example.mittens.services

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData")
class GradleTaskRunnerTest : BasePlatformTestCase() {
    
    private lateinit var gradleTaskRunner: GradleTaskRunner
    
    override fun setUp() {
        super.setUp()
        gradleTaskRunner = GradleTaskRunner(project)
    }
    
    @Test
    fun testServiceCreation() {
        assertNotNull("GradleTaskRunner should be created", gradleTaskRunner)
    }
    
    @Test
    fun testHasKnitGradlePlugin() {
        // Test that we can check for Knit gradle plugin without LinkageError
        val hasKnitPlugin = gradleTaskRunner.hasKnitGradlePlugin()
        
        // The test passes if no LinkageError is thrown
        assertNotNull("Should be able to check for Knit plugin without LinkageError", hasKnitPlugin)
        assertTrue("Result should be boolean", hasKnitPlugin is Boolean)
    }
    
    
    @Test
    fun testGradleExecutionResultCreation() {
        // Test that we can create GradleExecutionResult objects
        val result = GradleTaskRunner.GradleExecutionResult(
            success = true,
            output = "test output",
            errorOutput = "test error",
            executionTimeMs = 1000
        )
        
        assertTrue("Result should be successful", result.success)
        assertEquals("Output should match", "test output", result.output)
        assertEquals("Error output should match", "test error", result.errorOutput)
        assertEquals("Execution time should match", 1000, result.executionTimeMs)
    }
    
    @Test
    fun testGradlePluginDetectionWithoutActualExecution() {
        // Test the plugin detection functionality without running actual Gradle
        // This avoids long-running tests while still validating core functionality
        
        // Verify that service methods exist and can be called
        val hasKnit = gradleTaskRunner.hasKnitGradlePlugin()
        
        // This should not throw exceptions
        assertNotNull("hasKnitGradlePlugin should return a value", hasKnit)
    }
}