package com.moneyfan.demo

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLDivElement
import kotlinx.browser.document
import kotlinx.browser.window
import com.moneyfan.demo.components.*
import com.moneyfan.demo.data.SampleData
import com.moneyfan.demo.state.AppState
import com.moneyfan.demo.visualization.VisualizationManager

fun main() {
    window.onload = {
        val container = document.getElementById("root") as HTMLDivElement
        MainScope().launch {
            initializeApp(container)
        }
    }
}

internal suspend fun initializeApp(container: HTMLDivElement) {
    // Initialize application state
    val appState = AppState()
    
    // Create main layout
    val layout = createMainLayout(container)
    
    // Initialize visualization manager
    val visualizationManager = VisualizationManager(layout.visualizationContainer)
    
    // Load sample data
    val sampleData = SampleData.loadSampleData()
    appState.updateData(sampleData)
    
    // Initialize controls
    initializeControls(layout.controlsContainer, appState) { newState ->
        visualizationManager.updateVisualization(newState)
    }
    
    // Initial visualization
    visualizationManager.initializeVisualization(appState)
}

internal fun createMainLayout(container: HTMLDivElement): MainLayout {
    container.innerHTML = """
        <div class="app-container">
            <div class="header">
                <h1>MoneyFan Demo</h1>
                <div class="controls-container"></div>
            </div>
            <div class="main-content">
                <div class="visualization-container"></div>
                <div class="data-panel"></div>
            </div>
        </div>
    """.trimIndent()
    
    return MainLayout(
        controlsContainer = container.querySelector(".controls-container") as HTMLDivElement,
        visualizationContainer = container.querySelector(".visualization-container") as HTMLDivElement,
        dataPanel = container.querySelector(".data-panel") as HTMLDivElement
    )
}

internal fun initializeControls(
    container: HTMLDivElement,
    appState: AppState,
    onStateChange: (AppState) -> Unit
) {
    // Add control components
    container.appendChild(createTimeframeSelector(appState, onStateChange))
    container.appendChild(createIndicatorSelector(appState, onStateChange))
    container.appendChild(createViewModeSelector(appState, onStateChange))
}

data class MainLayout(
    val controlsContainer: HTMLDivElement,
    val visualizationContainer: HTMLDivElement,
    val dataPanel: HTMLDivElement
) 