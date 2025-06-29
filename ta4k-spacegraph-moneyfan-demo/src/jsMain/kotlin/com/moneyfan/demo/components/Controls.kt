package com.moneyfan.demo.components

import com.moneyfan.demo.state.AppState
import com.moneyfan.demo.state.Timeframe
import com.moneyfan.demo.state.Indicator
import com.moneyfan.demo.visualization.ViewMode
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

fun createTimeframeSelector(
    state: AppState,
    onStateChange: (AppState) -> Unit
): HTMLSelectElement {
    return document.createElement("select") as HTMLSelectElement.apply {
        className = "timeframe-selector"
        Timeframe.values().forEach { timeframe ->
            appendChild(document.createElement("option").apply {
                value = timeframe.name
                textContent = timeframe.name.replace("_", " ")
            })
        }
        value = state.selectedTimeframe.name
        addEventListener("change") { event ->
            val newTimeframe = Timeframe.valueOf((event.target as HTMLSelectElement).value)
            onStateChange(state.updateTimeframe(newTimeframe))
        }
    }
}

fun createIndicatorSelector(
    state: AppState,
    onStateChange: (AppState) -> Unit
): HTMLDivElement {
    return document.createElement("div") as HTMLDivElement.apply {
        className = "indicator-selector"
        Indicator.values().forEach { indicator ->
            appendChild(document.createElement("label").apply {
                appendChild(document.createElement("input").apply {
                    type = "checkbox"
                    checked = state.selectedIndicators.contains(indicator)
                    addEventListener("change") { event ->
                        val isChecked = (event.target as HTMLInputElement).checked
                        val newIndicators = if (isChecked) {
                            state.selectedIndicators + indicator
                        } else {
                            state.selectedIndicators - indicator
                        }
                        onStateChange(state.updateIndicators(newIndicators))
                    }
                })
                appendChild(document.createTextNode(indicator.name.replace("_", " ")))
            })
        }
    }
}

fun createViewModeSelector(
    state: AppState,
    onStateChange: (AppState) -> Unit
): HTMLSelectElement {
    return document.createElement("select") as HTMLSelectElement.apply {
        className = "view-mode-selector"
        ViewMode.values().forEach { mode ->
            appendChild(document.createElement("option").apply {
                value = mode.name
                textContent = mode.name.replace("_", " ")
            })
        }
        value = state.viewMode.name
        addEventListener("change") { event ->
            val newMode = ViewMode.valueOf((event.target as HTMLSelectElement).value)
            onStateChange(state.updateViewMode(newMode))
        }
    }
} 