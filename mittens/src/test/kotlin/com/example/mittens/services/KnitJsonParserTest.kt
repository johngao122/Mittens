package com.example.mittens.services

import com.example.mittens.model.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Comprehensive test suite for KnitJsonParser functionality
 */
class KnitJsonParserTest : LightJavaCodeInsightFixtureTestCase() {

    private lateinit var parser: KnitJsonParser
    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        parser = KnitJsonParser(project)
        tempDir = createTempDir("knit-json-test")
    }

    override fun tearDown() {
        super.tearDown()
        tempDir.deleteRecursively()
    }

    @Test
    fun testParseValidKnitJson() {
        val validKnitJson = """
        {
          "com/example/knit/demo/core/services/OrderService": {
            "parent": [
              "java.lang.Object"
            ],
            "injections": {
              "getInventoryService": {
                "methodId": "com.example.knit.demo.core.services.InventoryService.<init> -> com.example.knit.demo.core.services.InventoryService (GLOBAL)"
              }
            },
            "providers": [
              {
                "provider": "com.example.knit.demo.core.services.OrderService.<init> -> com.example.knit.demo.core.services.OrderService"
              }
            ]
          },
          "com/example/knit/demo/core/services/InventoryService": {
            "parent": [
              "java.lang.Object"
            ],
            "providers": [
              {
                "provider": "com.example.knit.demo.core.services.InventoryService.<init> -> com.example.knit.demo.core.services.InventoryService"
              }
            ]
          }
        }
        """.trimIndent()

        val testFile = File(tempDir, "knit.json")
        testFile.writeText(validKnitJson)

        val result = parser.parseKnitJson(testFile)

        assertTrue("Parsing should succeed", result.success)
        assertNotNull("Components should not be null", result.components)
        assertEquals("Should have 2 components", 2, result.components!!.size)
        
        val orderService = result.components!!["com/example/knit/demo/core/services/OrderService"]
        assertNotNull("OrderService should exist", orderService)
        assertEquals("OrderService should have 1 injection", 1, orderService!!.injections.size)
        assertEquals("OrderService should have 1 provider", 1, orderService.providers.size)
        
        val inventoryService = result.components!!["com/example/knit/demo/core/services/InventoryService"]
        assertNotNull("InventoryService should exist", inventoryService)
        assertEquals("InventoryService should have 0 injections", 0, inventoryService!!.injections.size)
        assertEquals("InventoryService should have 1 provider", 1, inventoryService.providers.size)
    }

    @Test
    fun testParseInvalidJson() {
        val invalidKnitJson = """
        {
          "invalid": "json
        }
        """.trimIndent()

        val testFile = File(tempDir, "invalid.json")
        testFile.writeText(invalidKnitJson)

        val result = parser.parseKnitJson(testFile)

        assertFalse("Parsing should fail for invalid JSON", result.success)
        assertNull("Components should be null on failure", result.components)
        assertNotNull("Error message should be present", result.errorMessage)
        assertTrue("Error message should mention JSON", result.errorMessage!!.contains("JSON"))
    }

    @Test
    fun testParseNonExistentFile() {
        val nonExistentFile = File(tempDir, "does-not-exist.json")

        val result = parser.parseKnitJson(nonExistentFile)

        assertFalse("Parsing should fail for non-existent file", result.success)
        assertNull("Components should be null on failure", result.components)
        assertNotNull("Error message should be present", result.errorMessage)
        assertTrue("Error message should mention file not found", result.errorMessage!!.contains("not found"))
    }

    @Test
    fun testConvertToKnitComponents() {
        val knitJsonRoot = mapOf(
            "com/example/test/ServiceA" to KnitJsonComponent(
                parent = listOf("java.lang.Object"),
                injections = mapOf(
                    "getDependency" to KnitInjection("com.example.test.ServiceB.<init> -> com.example.test.ServiceB (GLOBAL)")
                ),
                providers = listOf(
                    KnitJsonProvider("com.example.test.ServiceA.<init> -> com.example.test.ServiceA")
                )
            ),
            "com/example/test/ServiceB" to KnitJsonComponent(
                parent = listOf("java.lang.Object"),
                injections = emptyMap(),
                providers = listOf(
                    KnitJsonProvider("com.example.test.ServiceB.<init> -> com.example.test.ServiceB")
                )
            )
        )

        val components = parser.convertToKnitComponents(knitJsonRoot)

        assertEquals("Should have 2 components", 2, components.size)
        
        val serviceA = components.find { it.className == "ServiceA" }
        assertNotNull("ServiceA should exist", serviceA)
        assertEquals("ServiceA package should be correct", "com.example.test", serviceA!!.packageName)
        assertEquals("ServiceA should have 1 dependency", 1, serviceA.dependencies.size)
        assertEquals("ServiceA should have 1 provider", 1, serviceA.providers.size)
        assertEquals("ServiceA should be COMPOSITE type", ComponentType.COMPOSITE, serviceA.type)
        
        val serviceB = components.find { it.className == "ServiceB" }
        assertNotNull("ServiceB should exist", serviceB)
        assertEquals("ServiceB package should be correct", "com.example.test", serviceB!!.packageName)
        assertEquals("ServiceB should have 0 dependencies", 0, serviceB.dependencies.size)
        assertEquals("ServiceB should have 1 provider", 1, serviceB.providers.size)
        assertEquals("ServiceB should be PROVIDER type", ComponentType.PROVIDER, serviceB.type)
    }

    @Test
    fun testKnitJsonComponentHelperMethods() {
        val component = KnitJsonComponent()
        
        val testPath = "com/example/test/MyService"
        
        assertEquals("Should extract simple class name", "MyService", component.getSimpleClassName(testPath))
        assertEquals("Should extract package name", "com.example.test", component.getPackageName(testPath))
        
        val rootPath = "MyService"
        assertEquals("Should handle root class name", "MyService", component.getSimpleClassName(rootPath))
        assertEquals("Should handle empty package", "", component.getPackageName(rootPath))
    }

    @Test
    fun testKnitInjectionHelperMethods() {
        val injection = KnitInjection("com.example.test.ServiceA.<init> -> com.example.test.ServiceB (GLOBAL)")
        
        assertEquals("Should extract target type", "com.example.test.ServiceB", injection.getTargetType())
        assertEquals("Should extract provider class", "com.example.test.ServiceA", injection.getProviderClass())
        assertEquals("Should extract scope", "GLOBAL", injection.getScope())
        assertTrue("Should detect singleton", injection.isSingleton())
        
        val nonSingletonInjection = KnitInjection("com.example.ServiceA.method -> com.example.ServiceB")
        assertFalse("Should not be singleton without GLOBAL scope", nonSingletonInjection.isSingleton())
    }

    @Test
    fun testKnitProviderHelperMethods() {
        val provider = KnitJsonProvider("com.example.test.ServiceA.provideServiceB -> com.example.test.ServiceB")
        
        assertEquals("Should extract method name", "provideServiceB", provider.getMethodName())
        assertEquals("Should extract return type", "com.example.test.ServiceB", provider.getReturnType())
        assertEquals("Should extract provider class", "com.example.test.ServiceA", provider.getProviderClass())
        assertFalse("Should not have priority", provider.hasPriority())
        assertNull("Should not have priority value", provider.getPriority())
        
        val priorityProvider = KnitJsonProvider("priority: 5 com.example.ServiceA.provide -> com.example.ServiceB")
        assertTrue("Should have priority", priorityProvider.hasPriority())
        assertEquals("Should extract priority value", 5, priorityProvider.getPriority())
    }

    @Test
    fun testFilterKnitFrameworkComponents() {
        val knitJsonRoot = mapOf(
            "knit/DIStubImpl" to KnitJsonComponent(
                parent = listOf("knit.AbsDIStub")
            ),
            "knit/Option" to KnitJsonComponent(
                parent = listOf("kotlin.properties.ReadOnlyProperty")
            ),
            "com/example/test/MyService" to KnitJsonComponent(
                parent = listOf("java.lang.Object"),
                providers = listOf(
                    KnitJsonProvider("com.example.test.MyService.<init> -> com.example.test.MyService")
                )
            )
        )

        val components = parser.convertToKnitComponents(knitJsonRoot)

        assertEquals("Should filter out Knit framework components", 1, components.size)
        assertEquals("Should keep user components", "MyService", components.first().className)
    }

    @Test
    fun testValidateKnitJsonFile() {
        // Test valid file
        val validKnitJson = """{"test": {"parent": [], "injections": {}, "providers": []}}"""
        val validFile = File(tempDir, "valid.json")
        validFile.writeText(validKnitJson)
        
        assertTrue("Valid JSON file should pass validation", parser.validateKnitJsonFile(validFile))
        
        // Test invalid file
        val invalidKnitJson = """{"invalid": "json"""
        val invalidFile = File(tempDir, "invalid.json")
        invalidFile.writeText(invalidKnitJson)
        
        assertFalse("Invalid JSON file should fail validation", parser.validateKnitJsonFile(invalidFile))
        
        // Test non-existent file
        val nonExistentFile = File(tempDir, "does-not-exist.json")
        assertFalse("Non-existent file should fail validation", parser.validateKnitJsonFile(nonExistentFile))
    }

    @Test
    fun testComplexDependencyScenario() {
        val complexKnitJson = """
        {
          "com/example/complex/UserService": {
            "parent": ["java.lang.Object"],
            "injections": {
              "getUserRepository": {
                "methodId": "com.example.complex.DatabaseUserRepository.<init> -> com.example.complex.UserRepository (GLOBAL)"
              },
              "getValidationService": {
                "methodId": "com.example.complex.ValidationService.<init> -> com.example.complex.ValidationService (GLOBAL)"
              }
            },
            "providers": [
              {
                "provider": "com.example.complex.UserService.<init> -> com.example.complex.UserService"
              }
            ]
          },
          "com/example/complex/DatabaseUserRepository": {
            "parent": ["com.example.complex.UserRepository"],
            "providers": [
              {
                "provider": "com.example.complex.DatabaseUserRepository.<init> -> com.example.complex.UserRepository"
              }
            ]
          },
          "com/example/complex/ValidationService": {
            "parent": ["java.lang.Object"],
            "providers": [
              {
                "provider": "com.example.complex.ValidationService.<init> -> com.example.complex.ValidationService"
              },
              {
                "provider": "com.example.complex.ValidationService.provideEmailValidator -> com.example.complex.EmailValidator",
                "parameters": ["java.lang.String"]
              }
            ]
          }
        }
        """.trimIndent()

        val testFile = File(tempDir, "complex.json")
        testFile.writeText(complexKnitJson)

        val result = parser.parseKnitJson(testFile)
        assertTrue("Complex JSON should parse successfully", result.success)
        
        val components = parser.convertToKnitComponents(result.components!!)
        assertEquals("Should have 3 components", 3, components.size)
        
        val userService = components.find { it.className == "UserService" }
        assertNotNull("UserService should exist", userService)
        assertEquals("UserService should have 2 dependencies", 2, userService!!.dependencies.size)
        assertEquals("UserService should have 1 provider", 1, userService.providers.size)
        
        val validationService = components.find { it.className == "ValidationService" }
        assertNotNull("ValidationService should exist", validationService)
        assertEquals("ValidationService should have 2 providers", 2, validationService!!.providers.size)
    }
}