package borg.trikeshed.isam

interface IsamMetaFileReader {
    val constraints: Map<String, Any>
    val meta: Map<String, Any>
    
    fun read(): Map<String, Any>
    fun write(data: Map<String, Any>)
} 