package com.example.mittens.services

import com.example.mittens.model.DependencyGraph
import com.example.mittens.model.KnitComponent
import com.example.mittens.model.AnalysisResult
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*

@Service
class KnitAnalysisService(private val project: Project) {
    
    private val logger = thisLogger()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastAnalysisResult: AnalysisResult? = null
    private var isAnalysisRunning = false
    
    suspend fun runAnalysis(): AnalysisResult {
        if (isAnalysisRunning) {
            throw IllegalStateException("Analysis is already running")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                isAnalysisRunning = true
                logger.info("Starting Knit analysis for project: ${project.name}")
                
                // Phase 1: Just return a mock result for now
                val mockResult = AnalysisResult(
                    components = emptyList(),
                    dependencyGraph = DependencyGraph(emptyList(), emptyList()),
                    issues = emptyList(),
                    timestamp = System.currentTimeMillis(),
                    projectName = project.name
                )
                
                lastAnalysisResult = mockResult
                logger.info("Knit analysis completed successfully")
                mockResult
                
            } catch (e: Exception) {
                logger.error("Knit analysis failed", e)
                throw e
            } finally {
                isAnalysisRunning = false
            }
        }
    }
    
    fun getLastAnalysisResult(): AnalysisResult? = lastAnalysisResult
    
    fun isAnalysisRunning(): Boolean = isAnalysisRunning
    
    fun clearCache() {
        lastAnalysisResult = null
        logger.info("Analysis cache cleared")
    }
    
    fun dispose() {
        coroutineScope.cancel()
    }
}