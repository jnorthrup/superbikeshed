package borg.trikeshed.parse.json

actual object JsonImpl {
    actual fun parse(input: String): Any? {
        return try {
            js("JSON.parse(input)")
        } catch (e: Throwable) {
            null
        }
    }
    
    actual fun stringify(obj: Any?, pretty: Boolean): String {
        return try {
            if (pretty) {
                js("JSON.stringify(obj, null, 2)") as String
            } else {
                js("JSON.stringify(obj)") as String
            }
        } catch (e: Throwable) {
            "null"
        }
    }
}