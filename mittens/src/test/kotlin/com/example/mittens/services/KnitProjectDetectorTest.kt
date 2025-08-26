package com.example.mittens.services

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData")
class KnitProjectDetectorTest : BasePlatformTestCase() {
    
    private lateinit var projectDetector: KnitProjectDetector
    
    override fun setUp() {
        super.setUp()
        projectDetector = KnitProjectDetector(project)
    }
    
    @Test
    fun testServiceCreation() {
        assertNotNull("KnitProjectDetector should be created", projectDetector)
    }
    
    @Test
    fun testDetectKnitProjectWithEmptyProject() {
        // Test detection on an empty project
        val result = projectDetector.detectKnitProject()
        
        assertNotNull("Detection result should not be null", result)
        assertFalse("Empty project should not be detected as Knit project", result.isKnitProject)
        assertFalse("Empty project should not have Knit plugin", result.hasKnitPlugin)
        assertFalse("Empty project should not have Knit dependency", result.hasKnitDependency)
        assertNull("Empty project should not have Knit version", result.knitVersion)
        assertTrue("Empty project should have no components with by di", result.componentsWithByDi.isEmpty())
        assertTrue("Empty project should have no components with provides", result.componentsWithProvides.isEmpty())
        assertTrue("Empty project should have no components with component", result.componentsWithComponent.isEmpty())
    }
    
    @Test
    fun testKnitDetectionResultStructure() {
        val result = projectDetector.detectKnitProject()
        
        // Test that all fields are accessible and have expected types
        assertTrue("isKnitProject should be boolean", result.isKnitProject is Boolean)
        assertTrue("hasKnitPlugin should be boolean", result.hasKnitPlugin is Boolean)
        assertTrue("hasKnitDependency should be boolean", result.hasKnitDependency is Boolean)
        assertTrue("knitVersion should be null or string", result.knitVersion == null || result.knitVersion is String)
        assertTrue("componentsWithByDi should be list", result.componentsWithByDi is List<*>)
        assertTrue("componentsWithProvides should be list", result.componentsWithProvides is List<*>)
        assertTrue("componentsWithComponent should be list", result.componentsWithComponent is List<*>)
    }
    
    @Test
    fun testComponentInfoStructure() {
        // Test that ComponentInfo can be created and accessed
        val componentInfo = KnitProjectDetector.ComponentInfo(
            className = "TestComponent",
            packageName = "com.test",
            filePath = "/path/to/TestComponent.kt",
            detectionReason = "Has @Component annotation"
        )
        
        assertEquals("Class name should match", "TestComponent", componentInfo.className)
        assertEquals("Package name should match", "com.test", componentInfo.packageName)
        assertEquals("File path should match", "/path/to/TestComponent.kt", componentInfo.filePath)
        assertEquals("Detection reason should match", "Has @Component annotation", componentInfo.detectionReason)
    }
    
    @Test
    fun testDetectionResultWithMockData() {
        // Test creating a detection result with mock data
        val mockComponents = listOf(
            KnitProjectDetector.ComponentInfo("Component1", "com.test", "/Component1.kt", "@Component"),
            KnitProjectDetector.ComponentInfo("Component2", "com.test", "/Component2.kt", "by di")
        )
        
        val result = KnitProjectDetector.KnitDetectionResult(
            isKnitProject = true,
            hasKnitPlugin = true,
            hasKnitDependency = true,
            knitVersion = "1.0.0",
            componentsWithByDi = mockComponents,
            componentsWithProvides = emptyList(),
            componentsWithComponent = emptyList()
        )
        
        assertTrue("Should be detected as Knit project", result.isKnitProject)
        assertTrue("Should have Knit plugin", result.hasKnitPlugin)
        assertTrue("Should have Knit dependency", result.hasKnitDependency)
        assertEquals("Version should match", "1.0.0", result.knitVersion)
        assertEquals("Should have 2 components with by di", 2, result.componentsWithByDi.size)
    }
}