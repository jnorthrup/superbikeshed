package org.flatton.ui.rendering

import borg.trikeshed.lib.Indexed
import org.flatton.types.CouchDatabase

/**
 * A simple templating engine that mimics the functionality needed by Futon
 * to render data into columnar views. This demonstrates threading an "isomorph"
 * (a data structure) into a column-based layout.
 */
object FutonTemplate {

    private val propertyRegex = Regex("\\{\\{\\s*(\\w+)\\s*\\}\\}")
    private val eachRegex = Regex("\\{\\{#each (\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}", RegexOption.DOT_MATCHES_ALL)

    /**
     * Renders a template string with the given data context.
     * Supports simple property replacement `{{property}}` and looping `{{#each items}}...{{/each}}`.
     *
     * @param template The template string.
     * @param context The data context, typically a map or a data class instance.
     * @return The rendered string.
     */
    fun render(template: String, context: Map<String, Any?>): String {
        var result = template

        // Handle loops
        result = eachRegex.replace(result) { matchResult ->
            val listName = matchResult.groupValues[1]
            val innerTemplate = matchResult.groupValues[2]
            val items = context[listName] as? Indexed<*> ?: return@replace ""

            items.play.joinToString("") { item ->
                val itemContext = when (item) {
                    is CouchDatabase -> mapOf(
                        "name" to item.name.value,
                        "docCount" to item.status.docCount,
                        "updateSeq" to item.status.updateSeq,
                        "humanSize" to item.status.humanSize,
                        "encodedName" to item.name.value // simplified encoding
                    )
                    else -> mapOf("this" to item)
                }
                render(innerTemplate, itemContext)
            }
        }

        // Handle simple property replacement
        result = propertyRegex.replace(result) { matchResult ->
            val propName = matchResult.groupValues[1]
            context[propName]?.toString() ?: ""
        }

        return result
    }

    val databaseListTemplate = """
        <table class="databases table table-striped">
            <thead><th>Name</th><th>Size</th><th># of Docs</th><th>Update Seq</th><th>Actions</th></thead>
            <tbody>{{#each databases}}<tr><td><a href="#/database/{{encodedName}}/_all_docs">{{name}}</a></td><td>{{humanSize}}</td><td>{{docCount}}</td><td>{{updateSeq}}</td><td>Actions</td></tr>{{/each}}</tbody>
        </table>
    """.trimIndent()
}