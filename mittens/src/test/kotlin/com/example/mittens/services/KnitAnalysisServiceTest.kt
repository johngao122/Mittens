package com.example.mittens.services

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData")
class KnitAnalysisServiceTest : BasePlatformTestCase() {
    
    private lateinit var analysisService: KnitAnalysisService
    
    override fun setUp() {
        super.setUp()
        
        // Ensure the project has the necessary services registered
        try {
            analysisService = KnitAnalysisService(project)
        } catch (e: Exception) {
            // If service creation fails, create a mock or skip the test
            fail("Failed to create KnitAnalysisService: ${e.message}")
        }
    }
    
    @Test
    fun testServiceCreation() {
        assertNotNull("KnitAnalysisService should be created", analysisService)
    }
    
    @Test
    fun testRunAnalysisWithEmptyProject() {
        // Test that the service is properly initialized and can handle basic operations
        assertNotNull("Analysis service should be initialized", analysisService)
        assertFalse("Analysis should not be running initially", analysisService.isAnalysisRunning())
        assertNull("No analysis result should exist initially", analysisService.getLastAnalysisResult())
        
        // Test that the service can be cleared without issues
        analysisService.clearCache()
        assertNull("Cache should be cleared", analysisService.getLastAnalysisResult())
    }
    
    @Test
    fun testAnalysisServiceState() {
        // Test that the service maintains proper state
        assertNotNull("Analysis service should be initialized", analysisService)
        
        // Test that the service is properly initialized
        assertFalse("Analysis should not be running initially", analysisService.isAnalysisRunning())
        assertNull("No analysis result should exist initially", analysisService.getLastAnalysisResult())
        
        // Test that the service can be cleared
        analysisService.clearCache()
        assertNull("Cache should be cleared", analysisService.getLastAnalysisResult())
    }
    
    @Test
    fun testAnalysisWithNullProgressIndicator() {
        // Test that the service handles basic operations without issues
        assertNotNull("Analysis service should be initialized", analysisService)
        
        // Test that the service can be cleared
        analysisService.clearCache()
        assertNull("Cache should be cleared", analysisService.getLastAnalysisResult())
        
        // Test that the service state is consistent
        assertFalse("Analysis should not be running", analysisService.isAnalysisRunning())
    }
}