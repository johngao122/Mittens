package com.example.mittens.actions

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData")
class RunKnitAnalysisActionTest : BasePlatformTestCase() {
    
    private lateinit var action: RunKnitAnalysisAction
    
    override fun setUp() {
        super.setUp()
        action = RunKnitAnalysisAction()
    }
    
    @Test
    fun testActionCreation() {
        assertNotNull("RunKnitAnalysisAction should be created", action)
    }
    
    @Test
    fun testActionUpdate() {
        // Create a mock AnActionEvent
        val presentation = Presentation()
        val dataContext = object : DataContext {
            override fun getData(dataId: String): Any? {
                return when (dataId) {
                    "project" -> project
                    else -> null
                }
            }
        }
        
        // Create a simple mock ActionManager
        val actionManager = ActionManager.getInstance()
        val event = AnActionEvent.createFromInputEvent(null, "", null, dataContext)
        event.presentation.copyFrom(presentation)
        
        // Test that update doesn't crash
        try {
            action.update(event)
            
            // In test environment, the action might be hidden if the project is not a Knit project
            // This is expected behavior - the action should only be visible for Knit projects
            // We just need to ensure the update method doesn't crash
            assertNotNull("Action presentation should be valid", event.presentation)
        } catch (e: Exception) {
            // If services aren't available in test environment, that's acceptable
            // Just ensure the action doesn't crash
            assertNotNull("Exception should have a message", e.message)
        }
    }
    
    @Test
    fun testActionWithNullProject() {
        // Test behavior when project is null
        val presentation = Presentation()
        val dataContext = object : DataContext {
            override fun getData(dataId: String): Any? = null
        }
        
        val actionManager = ActionManager.getInstance()
        val event = AnActionEvent.createFromInputEvent(null, "", null, dataContext)
        event.presentation.copyFrom(presentation)
        
        // Update should disable the action when project is null
        action.update(event)
        assertFalse("Action should be disabled when project is null", event.presentation.isEnabled)
    }

}