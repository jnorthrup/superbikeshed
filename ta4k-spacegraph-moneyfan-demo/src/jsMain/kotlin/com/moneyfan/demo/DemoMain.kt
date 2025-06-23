package com.example.demo

import com.example.spacegraphkt.api.AgentAPI
import com.example.spacegraphkt.core.SpaceGraph // Kotlin wrapper for the JS SpaceGraph library
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.* // For HTML elements like HTMLButtonElement, HTMLInputElement, HTMLSelectElement, HTMLOptionElement

// Data classes and DSEL interface are assumed to be in the same 'com.example.demo' package
// and will be resolved by the Kotlin compiler.
// Imports for Series, Kline, etc., are within those files (DataHelper.kt, DselInterfaceAugmented.kt).

private val coroutineScope = MainScope()
private var agentApiInstance: AgentAPI? = null
private var demoVisualizerInstance: DemoVisualizer? = null

private var currentKlineDataSeries: Indexed<Kline>? = null
private var currentAugmentedVisualSeries: Indexed<VisualGraphPointWithMoneyfanOutcome>? = null

// Default values for UI elements, matching index.html defaults
private const val DEFAULT_ASSET_SYMBOL = "BTC"
private const val DEFAULT_BASELINE = 50000.0
private const val DEFAULT_SHORT_SMA = 10
private const val DEFAULT_LONG_SMA = 20
private const val DEFAULT_RSI_PERIOD = 14

/**
 * Main entry point for the Kotlin/JS application.
 * Initializes the demo when the browser window has loaded.
 */
fun main() {
    window.onload = {
        val spaceContainer = document.getElementById("space-container") as? HTMLDivElement
        if (spaceContainer == null) {
            updateStatus("Fatal Error: #space-container element not found! Cannot initialize SpaceGraph.", true)
            return@onload
        }

        try {
            // It's assumed that spacegraph.js (and its dependencies like THREE.js) are loaded globally
            // or correctly managed by the JS build if kotlin-spacegraph doesn't bundle them.
            val spaceGraphJsInstance = SpaceGraph(spaceContainer, null) // Pass null for default UI elements config
            agentApiInstance = AgentAPI(spaceGraphJsInstance)
            spaceGraphJsInstance.agentApi = agentApiInstance // For events from core SpaceGraph back to AgentAPI listeners

            // Expose to JS console for debugging if needed
            window.asDynamic().spaceGraphAgent = agentApiInstance
            window.asDynamic().spaceGraphInstance = spaceGraphJsInstance


            demoVisualizerInstance = DemoVisualizer(agentApiInstance!!)

            updateStatus("Demo Initialized. Ready to load data.", false)

        } catch (e: Exception) {
            val errorMessage = "Error initializing SpaceGraph/AgentAPI: ${e.message}"
            updateStatus(errorMessage, true)
            spaceContainer.innerHTML = "<p style='color:red; padding:20px;'>$errorMessage</p>"
            return@onload
        }

        initializeUIValues()
        setupDemoUIEventListeners()
        console.log("DemoMain: Initialization complete, UI listeners attached.")
        // Optionally, trigger initial data load:
        // coroutineScope.launch { loadAndDisplayDataWithCurrentSettings() }
    }
}

/**
 * Sets initial values for UI input fields and populates dropdowns.
 */
fun initializeUIValues() {
    (document.getElementById("shortSmaInput") as? HTMLInputElement)?.value = DEFAULT_SHORT_SMA.toString()
    (document.getElementById("longSmaInput") as? HTMLInputElement)?.value = DEFAULT_LONG_SMA.toString()
    (document.getElementById("rsiPeriodInput") as? HTMLInputElement)?.value = DEFAULT_RSI_PERIOD.toString()
    (document.getElementById("assetSymbolInput") as? HTMLInputElement)?.value = DEFAULT_ASSET_SYMBOL
    (document.getElementById("baselineInput") as? HTMLInputElement)?.value = DEFAULT_BASELINE.toString()

    // Initialize ADZ/CP toggles based on DselInterfaceAugmented defaults (which might load from a saved state in a real app)
    // For this demo, DselInterfaceAugmented.assetConfigs is initially empty or gets default.
    val initialAssetConfig = DselInterfaceAugmented.assetConfigs.getOrPut(DEFAULT_ASSET_SYMBOL) {
        DemoMoneyfanAssetConfig(DEFAULT_ASSET_SYMBOL, DEFAULT_BASELINE)
    }
    (document.getElementById("adzToggle") as? HTMLInputElement)?.checked = initialAssetConfig.adzActive
    (document.getElementById("cpToggle") as? HTMLInputElement)?.checked = DselInterfaceAugmented.crashProtectionActiveGlobally

    val attentionSelect = document.getElementById("attentionSelect") as? HTMLSelectElement
    attentionSelect?.innerHTML = "" // Clear existing options if any
    val defaultOption = document.createElement("option") as HTMLOptionElement
    defaultOption.value = "NONE_MF"
    defaultOption.text = "None"
    attentionSelect?.add(defaultOption)

    MoneyfanActionType.values().forEach { actionType ->
        val option = document.createElement("option") as HTMLOptionElement
        option.value = actionType.name
        option.text = actionType.name.replace('_', ' ').capitalizeWords()
        attentionSelect?.add(option)
    }
    attentionSelect?.value = "NONE_MF"
}

/**
 * Attaches event listeners to UI controls.
 */
fun setupDemoUIEventListeners() {
    (document.getElementById("loadDataBtn") as? HTMLButtonElement)?.onclick = {
        coroutineScope.launch { loadAndDisplayDataWithCurrentSettings() }
    }

    (document.getElementById("reprocessBtn") as? HTMLButtonElement)?.onclick = {
        // No coroutine needed if processAndDisplay is not suspend, but good practice if it might evolve
        coroutineScope.launch { processAndDisplayCurrentDataWithUISettings() }
    }

    (document.getElementById("updateBaselineBtn") as? HTMLButtonElement)?.onclick = {
        val symbol = (document.getElementById("assetSymbolInput") as? HTMLInputElement)?.value?.trim() ?: DEFAULT_ASSET_SYMBOL
        val newBaseline = (document.getElementById("baselineInput") as? HTMLInputElement)?.value?.toDoubleOrNull()
        if (newBaseline != null && newBaseline > 0) {
            DselInterfaceAugmented.updateBaseline(symbol, newBaseline)
            updateStatus("Baseline for '$symbol' updated to ${newBaseline.toFixed(2)}. Apply & Redraw to see changes.", false)
        } else {
            updateStatus("Invalid baseline value. Please enter a positive number.", true)
        }
    }

    (document.getElementById("adzToggle") as? HTMLInputElement)?.onchange = { event ->
        val symbol = (document.getElementById("assetSymbolInput") as? HTMLInputElement)?.value?.trim() ?: DEFAULT_ASSET_SYMBOL
        val isChecked = (event.target as HTMLInputElement).checked
        DselInterfaceAugmented.toggleADZ(symbol) // This function now returns the new state
        updateStatus("ADZ for '$symbol' ${if(isChecked) "ENABLED" else "DISABLED"}. Apply & Redraw.", false)
    }

    (document.getElementById("cpToggle") as? HTMLInputElement)?.onchange = { event ->
        val isChecked = (event.target as HTMLInputElement).checked
        DselInterfaceAugmented.crashProtectionActiveGlobally = isChecked
        updateStatus("Global Crash Protection ${if(isChecked) "ENABLED" else "DISABLED"}. Apply & Redraw.", false)
    }

    (document.getElementById("attentionSelect") as? HTMLSelectElement)?.onchange = { event ->
        val selectedActionName = (event.target as HTMLSelectElement).value
        if (selectedActionName == "NONE_MF") {
            if (currentAugmentedVisualSeries != null) {
                 demoVisualizerInstance?.displayDataWithMoneyfanOutcomes(currentAugmentedVisualSeries!!) // Re-display to reset styles
                 updateStatus("Attention highlights reset.", false)
            }
        } else {
            try {
                val actionType = MoneyfanActionType.valueOf(selectedActionName)
                applyAttentionFocusToMoneyfanAction(actionType)
            } catch (e: IllegalArgumentException) {
                updateStatus("Invalid action type for highlight: $selectedActionName", true)
            }
        }
    }
     // Asset symbol input change - might clear/reset things or just be used on reprocess
    (document.getElementById("assetSymbolInput") as? HTMLInputElement)?.onchange = { event ->
        val newSymbol = (event.target as HTMLInputElement).value.trim().toUpperCase()
        if (newSymbol.isNotBlank()) {
            updateStatus("Asset symbol changed to '$newSymbol'. Baseline may need adjustment. Apply & Redraw.", false)
            // UI could also update baseline input if new symbol has a stored config
            val config = DselInterfaceAugmented.assetConfigs[newSymbol]
            (document.getElementById("baselineInput") as? HTMLInputElement)?.value = config?.baseline?.toString() ?: DEFAULT_BASELINE.toString()
            (document.getElementById("adzToggle") as? HTMLInputElement)?.checked = config?.adzActive ?: false
        }
    }
}

/**
 * Fetches kline data then processes and displays it with current UI settings.
 */
suspend fun loadAndDisplayDataWithCurrentSettings() {
    updateStatus("Loading kline data...", true) // Use true for isError to make it stand out as "busy" potentially
    // Assuming final-BTC-USDT-1m.csv is in ./data/ relative to index.html
    currentKlineDataSeries = fetchAndParseKlineDataToSeries("./data/final-BTC-USDT-1m.csv")

    if (currentKlineDataSeries == null || currentKlineDataSeries!!.size == 0) {
        updateStatus("Failed to load or parse kline data. Check file path and format. (Expected ./data/final-BTC-USDT-1m.csv)", true)
        currentAugmentedVisualSeries = null
        demoVisualizerInstance?.clearGraph()
        return
    }
    updateStatus("Kline data loaded (${currentKlineDataSeries!!.size} records). Processing with UI settings...", false)
    processAndDisplayCurrentDataWithUISettings() // Call the processing function
}

/**
 * Processes currently loaded kline data using TA and Moneyfan parameters from the UI,
 * then updates the SpaceGraph visualization.
 */
fun processAndDisplayCurrentDataWithUISettings() {
    if (currentKlineDataSeries == null || currentKlineDataSeries!!.size == 0) {
        updateStatus("No kline data loaded. Please load data first.", true)
        return
    }

    // Update TA parameters from UI
    DselInterfaceAugmented.shortSmaPeriod = (document.getElementById("shortSmaInput") as? HTMLInputElement)?.value?.toIntOrNull() ?: DEFAULT_SHORT_SMA
    DselInterfaceAugmented.longSmaPeriod = (document.getElementById("longSmaInput") as? HTMLInputElement)?.value?.toIntOrNull() ?: DEFAULT_LONG_SMA
    DselInterfaceAugmented.rsiPeriod = (document.getElementById("rsiPeriodInput") as? HTMLInputElement)?.value?.toIntOrNull() ?: DEFAULT_RSI_PERIOD

    val assetSymbol = (document.getElementById("assetSymbolInput") as? HTMLInputElement)?.value?.trim()?.toUpperCase() ?: DEFAULT_ASSET_SYMBOL
    // Ensure config exists for this symbol, baseline is already updated by its button or init
     DselInterfaceAugmented.assetConfigs.getOrPut(assetSymbol) {
        val baselineValue = (document.getElementById("baselineInput") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: DEFAULT_BASELINE
        DemoMoneyfanAssetConfig(assetSymbol, baselineValue)
    }
    // ADZ and CP global states are directly updated by their event handlers.

    updateStatus("Processing data for '$assetSymbol' with current settings...", false)
    currentAugmentedVisualSeries = DselInterfaceAugmented.generateAugmentedVisualData(
        currentKlineDataSeries!!,
        assetSymbol
    )

    if (currentAugmentedVisualSeries == null || currentAugmentedVisualSeries!!.size == 0) {
        updateStatus("Failed to generate augmented visual data. Check console for errors.", true)
        demoVisualizerInstance?.clearGraph()
        return
    }

    demoVisualizerInstance?.displayDataWithMoneyfanOutcomes(currentAugmentedVisualSeries!!)
    updateStatus("'$assetSymbol' data displayed with current TA/Moneyfan settings.", false)

    val selectedSignalName = (document.getElementById("attentionSelect") as? HTMLSelectElement)?.value ?: "NONE_MF"
    if (selectedSignalName != "NONE_MF") {
        try { applyAttentionFocusToMoneyfanAction(MoneyfanActionType.valueOf(selectedSignalName)) }
        catch (e: IllegalArgumentException) { /* ignore */ }
    } else { // Explicitly reset highlights if "None" is selected after reprocess
         if (currentAugmentedVisualSeries != null) {
             demoVisualizerInstance?.highlightMoneyfanAction(null, currentAugmentedVisualSeries!!)
         }
    }
}

/**
 * Applies visual highlighting in SpaceGraph for nodes matching a specific MoneyfanActionType.
 * @param actionType The MoneyfanActionType to highlight.
 */
fun applyAttentionFocusToMoneyfanAction(actionType: MoneyfanActionType) {
    if (currentAugmentedVisualSeries != null && demoVisualizerInstance != null) {
        updateStatus("Highlighting Moneyfan Action: $actionType...", false)
        demoVisualizerInstance!!.highlightMoneyfanAction(actionType, currentAugmentedVisualSeries!!)
    } else {
        updateStatus("No visual data loaded to apply attention focus.", true)
    }
}

/**
 * Updates the content and style of the status bar.
 * @param message The message to display.
 * @param isError If true, styles the message as an error.
 */
fun updateStatus(message: String, isError: Boolean = false) {
    val statusBar = document.getElementById("status-bar") as? HTMLDivElement
    statusBar?.textContent = message
    if (statusBar != null) { // Check for null before accessing style
        statusBar.style.color = if (isError) "#FF6B6B" else "#B0B0B0" // Brighter red for error
        statusBar.style.fontWeight = if (isError) "bold" else "normal"
    }
    if (isError) {
        console.error("Status Error: $message")
    } else {
        console.log("Status: $message")
    }
}

/**
 * Helper extension to capitalize words in a string (e.g., for display in dropdowns).
 */
fun String.capitalizeWords(): String = this.split(" ").joinToString(" ") { word ->
    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(kotlin.ULocale.getDefault()) else it.toString() }
}

// Helper for toFixed on Double, assuming it's defined in DemoVisualizer.kt or common utils
// fun Double.toFixed(digits: Int): String = this.asDynamic().toFixed(digits) as String


// Ensure other necessary files (DataHelper.kt, DselInterfaceAugmented.kt, DemoVisualizer.kt, DemoDataClasses.kt)
// are correctly defined in the com.example.demo package.
