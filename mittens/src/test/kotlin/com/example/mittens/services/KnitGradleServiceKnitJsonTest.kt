package com.example.mittens.services

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import java.io.File

/**
 * Test suite for KnitGradleService knit.json-related functionality
 */
class KnitGradleServiceKnitJsonTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var gradleService: KnitGradleService

    override fun setUp() {
        super.setUp()
        gradleService = KnitGradleService(project)
    }

    @Test
    fun testKnitJsonConfigurationDetection() {
        // Test build.gradle.kts with knit.json configuration
        val buildGradleWithKnitJson = """
            plugins {
                kotlin("jvm") version "1.9.22"
                id("io.github.tiktok.knit.plugin") version "0.1.5"
            }
            
            apply(plugin = "io.github.tiktok.knit.plugin")
            
            extensions.getByType<KnitExtension>().apply {
                dependencyTreeOutputPath.set("build/knit.json")
            }
            
            dependencies {
                implementation("io.github.tiktok.knit:knit:0.1.5")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithKnitJson)

        assertTrue("Should detect knit.json configuration", gradleService.hasKnitJsonConfiguration())
        assertEquals("Should return default knit.json path", "build/knit.json", gradleService.getConfiguredKnitJsonOutputPath())
    }

    @Test
    fun testCustomKnitJsonOutputPath() {
        val buildGradleWithCustomPath = """
            plugins {
                kotlin("jvm") version "1.9.22"
                id("io.github.tiktok.knit.plugin") version "0.1.5"
            }
            
            extensions.getByType<KnitExtension>().apply {
                dependencyTreeOutputPath.set("custom/path/dependencies.json")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithCustomPath)

        assertTrue("Should detect knit.json configuration", gradleService.hasKnitJsonConfiguration())
        assertEquals("Should return custom knit.json path", "custom/path/dependencies.json", gradleService.getConfiguredKnitJsonOutputPath())
    }

    @Test
    fun testKnitJsonConfigurationMissing() {
        val buildGradleWithoutKnitJson = """
            plugins {
                kotlin("jvm") version "1.9.22"
                id("io.github.tiktok.knit.plugin") version "0.1.5"
            }
            
            dependencies {
                implementation("io.github.tiktok.knit:knit:0.1.5")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithoutKnitJson)

        assertFalse("Should not detect knit.json configuration", gradleService.hasKnitJsonConfiguration())
        assertEquals("Should return default path when not configured", "build/knit.json", gradleService.getConfiguredKnitJsonOutputPath())
    }

    @Test
    fun testKnitJsonFileOperations() {
        // Since we can't easily create actual build directory in test, 
        // we test the path construction logic
        val knitJsonPath = gradleService.getKnitJsonPath()
        
        // The path should be constructed correctly even if file doesn't exist
        if (knitJsonPath != null) {
            assertTrue("knit.json path should end with knit.json", knitJsonPath.endsWith("knit.json"))
        }
        
        // Test file existence check - should return false when file doesn't exist
        assertFalse("Should return false when knit.json doesn't exist", gradleService.hasKnitJsonFile())
        
        // Test last modified check - should return null when file doesn't exist
        assertNull("Should return null timestamp when file doesn't exist", gradleService.getKnitJsonLastModified())
    }

    @Test
    fun testKnitJsonInfo() {
        val info = gradleService.getKnitJsonInfo()
        assertNotNull("Should return info string", info)
        assertTrue("Info should contain 'knit.json'", info.contains("knit.json"))
        
        // Since file likely doesn't exist in test environment, should indicate that
        assertTrue("Info should indicate file status", 
                  info.contains("not found") || info.contains("Build directory"))
    }

    @Test
    fun testIsKnitJsonNewer() {
        val currentTime = System.currentTimeMillis()
        val pastTime = currentTime - 10000 // 10 seconds ago
        
        // When knit.json doesn't exist, should return false
        assertFalse("Should return false when file doesn't exist", gradleService.isKnitJsonNewer(pastTime))
        assertFalse("Should return false when file doesn't exist", gradleService.isKnitJsonNewer(currentTime))
    }

    @Test
    fun testKnitJsonPathConstruction() {
        // Test that getKnitJsonFile returns correct path structure
        val knitJsonFile = gradleService.getKnitJsonFile()
        
        if (knitJsonFile != null) {
            assertEquals("File name should be knit.json", "knit.json", knitJsonFile.name)
            assertTrue("Path should contain build directory", knitJsonFile.path.contains("build"))
        } else {
            // This is acceptable in test environment where project.basePath might be null
            assertNull("Build directory path should be null in test environment", gradleService.getBuildDir())
        }
    }

    @Test
    fun testKnitProjectDetection() {
        // Test with Knit project configuration
        val buildGradleWithKnit = """
            plugins {
                kotlin("jvm") version "1.9.22"
                id("io.github.tiktok.knit.plugin") version "0.1.5"
            }
            
            dependencies {
                implementation("io.github.tiktok.knit:knit:0.1.5")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithKnit)

        assertTrue("Should detect Knit project", gradleService.isKnitProject())
        assertNotNull("Should find Knit configuration", gradleService.findKnitGradleConfiguration())
        assertNotNull("Should detect Knit version", gradleService.getKnitVersion())
    }

    @Test
    fun testNonKnitProject() {
        val buildGradleWithoutKnit = """
            plugins {
                kotlin("jvm") version "1.9.22"
            }
            
            dependencies {
                implementation("org.springframework:spring-core:5.3.21")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithoutKnit)

        assertFalse("Should not detect Knit project", gradleService.isKnitProject())
        assertNull("Should not find Knit configuration", gradleService.findKnitGradleConfiguration())
        assertNull("Should not detect Knit version", gradleService.getKnitVersion())
    }

    @Test
    fun testKnitVersionExtraction() {
        val buildGradleWithVersion = """
            dependencies {
                implementation("io.github.tiktok.knit:knit:0.1.5")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            }
        """.trimIndent()

        myFixture.configureByText("build.gradle.kts", buildGradleWithVersion)

        val version = gradleService.getKnitVersion()
        assertEquals("Should extract correct version", "0.1.5", version)
    }

    @Test
    fun testAlternativeKnitDependencyFormats() {
        // Test different ways of specifying Knit dependency
        val formats = listOf(
            """implementation("io.github.tiktok.knit:knit:0.1.4")""",
            """implementation("io.github.tiktok:knit:0.1.4")""",
            """implementation 'io.github.tiktok.knit:knit:0.1.4'""",
            """implementation 'io.github.tiktok:knit:0.1.4'"""
        )

        formats.forEach { dependencyFormat ->
            val buildGradle = """
                plugins {
                    id("io.github.tiktok.knit.plugin") version "0.1.4"
                }
                
                dependencies {
                    $dependencyFormat
                }
            """.trimIndent()

            myFixture.configureByText("build.gradle.kts", buildGradle)
            
            assertTrue("Should detect Knit project with format: $dependencyFormat", gradleService.isKnitProject())
        }
    }
}