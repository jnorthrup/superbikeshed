package io.trikeshed.couchdb.ui

import androidx.compose.runtime.*
import io.trikeshed.couchdb.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

@Composable
fun App() {
    var client by remember { mutableStateOf<CouchDBClient?>(null) }
    var databases by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDb by remember { mutableStateOf<String?>(null) }
    var dbInfo by remember { mutableStateOf<DatabaseInfo?>(null) }
    var showCreateDb by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client) {
        client?.let {
            try {
                databases = it.getDatabases()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    LaunchedEffect(selectedDb) {
        selectedDb?.let { dbName ->
            client?.let {
                try {
                    dbInfo = it.getDatabaseInfo(dbName)
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            padding(16.px)
            gap(16.px)
        }
    }) {
        // Header
        H1({ style { margin(0.px) } }) {
            Text("CouchDB Flatton")
        }

        // Error display
        error?.let {
            Div({
                style {
                    padding(8.px)
                    backgroundColor(Color.red.copy(alpha = 0.1))
                    borderRadius(4.px)
                }
            }) {
                Text(it)
            }
        }

        // Database list
        Div({
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Row)
                gap(16.px)
            }
        }) {
            // Sidebar
            Div({
                style {
                    width(200.px)
                    padding(8.px)
                    backgroundColor(Color.lightgray)
                    borderRadius(4.px)
                }
            }) {
                H2({ style { margin(0.px) } }) {
                    Text("Databases")
                }
                
                Button({
                    onClick { showCreateDb = true }
                    style {
                        width(100.percent)
                        marginBottom(8.px)
                    }
                }) {
                    Text("Create Database")
                }

                databases.forEach { dbName ->
                    Div({
                        onClick { selectedDb = dbName }
                        style {
                            padding(8.px)
                            cursor("pointer")
                            backgroundColor(if (selectedDb == dbName) Color.lightblue else Color.transparent)
                            borderRadius(4.px)
                        }
                    }) {
                        Text(dbName)
                    }
                }
            }

            // Main content
            Div({
                style {
                    flex(1)
                    padding(16.px)
                    backgroundColor(Color.white)
                    borderRadius(4.px)
                    boxShadow("0 2px 4px rgba(0,0,0,0.1)")
                }
            }) {
                selectedDb?.let { dbName ->
                    dbInfo?.let { info ->
                        H2({ style { margin(0.px) } }) {
                            Text(dbName)
                        }
                        
                        Table({
                            style {
                                width(100.percent)
                                borderCollapse("collapse")
                            }
                        }) {
                            Tr {
                                Th { Text("Property") }
                                Th { Text("Value") }
                            }
                            Tr {
                                Td { Text("Document Count") }
                                Td { Text(info.doc_count.toString()) }
                            }
                            Tr {
                                Td { Text("Update Sequence") }
                                Td { Text(info.update_seq) }
                            }
                            Tr {
                                Td { Text("Disk Size") }
                                Td { Text("${info.disk_size} bytes") }
                            }
                        }
                    }
                } ?: run {
                    Text("Select a database to view details")
                }
            }
        }
    }

    // Create database dialog
    if (showCreateDb) {
        Div({
            style {
                position("fixed")
                top(0.px)
                left(0.px)
                right(0.px)
                bottom(0.px)
                backgroundColor(Color.black.copy(alpha = 0.5))
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                justifyContent(JustifyContent.Center)
            }
        }) {
            Div({
                style {
                    backgroundColor(Color.white)
                    padding(16.px)
                    borderRadius(4.px)
                    width(300.px)
                }
            }) {
                H3({ style { margin(0.px) } }) {
                    Text("Create Database")
                }
                
                Input({
                    value(newDbName)
                    onInput { newDbName = it.value }
                    style {
                        width(100.percent)
                        marginBottom(8.px)
                    }
                })
                
                Div({
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.FlexEnd)
                        gap(8.px)
                    }
                }) {
                    Button({
                        onClick { showCreateDb = false }
                    }) {
                        Text("Cancel")
                    }
                    
                    Button({
                        onClick {
                            client?.let {
                                try {
                                    it.createDatabase(newDbName)
                                    databases = it.getDatabases()
                                    showCreateDb = false
                                    newDbName = ""
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        }
                    }) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

fun main() {
    val client = CouchDBClientJs(
        CouchDBConfig(
            url = "http://localhost:5984",
            username = "admin",
            password = "password"
        )
    )
    
    renderComposable(rootElementId = "root") {
        App()
    }
} 