package com.example.mittens.settings

import com.example.mittens.services.KnitSettingsService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class KnitAnalysisConfigurable : Configurable {
    
    private var component: DialogPanel? = null
    private val settingsService = KnitSettingsService.getInstance()
    
    override fun getDisplayName(): String = "Knit Analysis"
    
    override fun createComponent(): JComponent? {
        component = panel {
            group("Analysis Settings") {
                row {
                    checkBox("Auto-analyze after build")
                        .bindSelected(
                            { settingsService.isAutoAnalyzeEnabled() },
                            { settingsService.setAutoAnalyzeEnabled(it) }
                        )
                        .comment("Automatically run Knit analysis when project builds complete")
                }
                
                row {
                    checkBox("Show graph on startup")
                        .bindSelected(
                            { settingsService.isShowGraphOnStartup() },
                            { settingsService.setShowGraphOnStartup(it) }
                        )
                        .comment("Open the dependency graph tool window when analysis completes")
                }
                
                row("Max nodes in graph:") {
                    intTextField()
                        .bindIntText(
                            { settingsService.getMaxNodesInGraph() },
                            { settingsService.setMaxNodesInGraph(it) }
                        )
                        .columns(COLUMNS_SHORT)
                        .comment("Limit graph visualization to prevent performance issues")
                }.layout(RowLayout.LABEL_ALIGNED)
                
                row {
                    checkBox("Enable detailed logging")
                        .bindSelected(
                            { settingsService.isDetailedLoggingEnabled() },
                            { settingsService.setDetailedLoggingEnabled(it) }
                        )
                        .comment("Enable verbose logging for debugging purposes")
                }
            }
            
            group("Graph Visualization") {
                row {
                    text("Graph visualization settings will be added in Phase 5")
                        .applyToComponent { isEnabled = false }
                }
            }
            
            group("Export Options") {
                row {
                    text("Export configuration will be added in Phase 7")
                        .applyToComponent { isEnabled = false }
                }
            }
        }
        
        return component
    }
    
    override fun isModified(): Boolean {
        return component?.isModified() ?: false
    }
    
    @Throws(ConfigurationException::class)
    override fun apply() {
        component?.apply()
    }
    
    override fun reset() {
        component?.reset()
    }
    
    override fun disposeUIResources() {
        component = null
    }
}