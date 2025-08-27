package com.example.mittens.services

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "KnitAnalysisSettings",
    storages = [Storage("KnitAnalysisPlugin.xml")]
)
class KnitSettingsService : PersistentStateComponent<KnitSettingsService.State> {

    private var state = State()

    data class State(
        var autoAnalyzeAfterBuild: Boolean = false,
        var showGraphOnStartup: Boolean = true,
        var maxNodesInGraph: Int = 500,
        var enableDetailedLogging: Boolean = false,
        var enableIssueValidation: Boolean = true,
        var confidenceThreshold: Double = 0.3,
        var enableAccuracyReporting: Boolean = true,
        var trackAccuracyTrends: Boolean = true
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    fun isAutoAnalyzeEnabled(): Boolean = state.autoAnalyzeAfterBuild
    fun setAutoAnalyzeEnabled(enabled: Boolean) {
        state.autoAnalyzeAfterBuild = enabled
    }

    fun isShowGraphOnStartup(): Boolean = state.showGraphOnStartup
    fun setShowGraphOnStartup(show: Boolean) {
        state.showGraphOnStartup = show
    }

    fun getMaxNodesInGraph(): Int = state.maxNodesInGraph
    fun setMaxNodesInGraph(max: Int) {
        state.maxNodesInGraph = max
    }

    fun isDetailedLoggingEnabled(): Boolean = state.enableDetailedLogging
    fun setDetailedLoggingEnabled(enabled: Boolean) {
        state.enableDetailedLogging = enabled
    }

    fun isValidationEnabled(): Boolean = state.enableIssueValidation
    fun setValidationEnabled(enabled: Boolean) {
        state.enableIssueValidation = enabled
    }

    fun getConfidenceThreshold(): Double = state.confidenceThreshold
    fun setConfidenceThreshold(threshold: Double) {
        state.confidenceThreshold = threshold.coerceIn(0.0, 1.0)
    }

    fun isAccuracyReportingEnabled(): Boolean = state.enableAccuracyReporting
    fun setAccuracyReportingEnabled(enabled: Boolean) {
        state.enableAccuracyReporting = enabled
    }

    fun isAccuracyTrendTrackingEnabled(): Boolean = state.trackAccuracyTrends
    fun setAccuracyTrendTrackingEnabled(enabled: Boolean) {
        state.trackAccuracyTrends = enabled
    }

    companion object {
        fun getInstance(): KnitSettingsService = service()
    }
}