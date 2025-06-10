package k2script.engine

class Context private constructor(val path: String) {
    companion object {
        fun global() = Context("global")
        fun script(path: String) = Context(path)
    }
    
    fun provide(service: Any, type: Class<*>) = this
} 