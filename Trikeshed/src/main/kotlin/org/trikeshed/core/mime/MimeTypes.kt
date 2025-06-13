package org.trikeshed.core.mime

/**
 * Common MIME types and their file extensions.
 * This enum provides a type-safe way to handle MIME types and their associated extensions.
 */
enum class MimeTypes(val mimeType: String, val extensions: Set<String>) {
    // Text types
    TEXT_PLAIN("text/plain", setOf("txt", "text")),
    TEXT_HTML("text/html", setOf("html", "htm")),
    TEXT_CSS("text/css", setOf("css")),
    TEXT_JAVASCRIPT("text/javascript", setOf("js")),
    TEXT_CSV("text/csv", setOf("csv")),
    TEXT_XML("text/xml", setOf("xml")),
    TEXT_MARKDOWN("text/markdown", setOf("md", "markdown")),
    
    // Image types
    IMAGE_JPEG("image/jpeg", setOf("jpg", "jpeg")),
    IMAGE_PNG("image/png", setOf("png")),
    IMAGE_GIF("image/gif", setOf("gif")),
    IMAGE_SVG("image/svg+xml", setOf("svg")),
    IMAGE_WEBP("image/webp", setOf("webp")),
    IMAGE_ICO("image/x-icon", setOf("ico")),
    
    // Audio types
    AUDIO_MPEG("audio/mpeg", setOf("mp3", "mpeg")),
    AUDIO_WAV("audio/wav", setOf("wav")),
    AUDIO_OGG("audio/ogg", setOf("ogg")),
    AUDIO_WEBM("audio/webm", setOf("webm")),
    
    // Video types
    VIDEO_MP4("video/mp4", setOf("mp4")),
    VIDEO_WEBM("video/webm", setOf("webm")),
    VIDEO_OGG("video/ogg", setOf("ogv")),
    
    // Application types
    APPLICATION_JSON("application/json", setOf("json")),
    APPLICATION_XML("application/xml", setOf("xml")),
    APPLICATION_PDF("application/pdf", setOf("pdf")),
    APPLICATION_ZIP("application/zip", setOf("zip")),
    APPLICATION_GZIP("application/gzip", setOf("gz")),
    APPLICATION_OCTET_STREAM("application/octet-stream", setOf("bin", "dat")),
    APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded", emptySet()),
    APPLICATION_FORM_DATA("multipart/form-data", emptySet()),
    
    // Font types
    FONT_TTF("font/ttf", setOf("ttf")),
    FONT_OTF("font/otf", setOf("otf")),
    FONT_WOFF("font/woff", setOf("woff")),
    FONT_WOFF2("font/woff2", setOf("woff2")),
    
    // Unknown type
    UNKNOWN("application/octet-stream", emptySet());
    
    companion object {
        private val extensionToMimeType = values().flatMap { mimeType ->
            mimeType.extensions.map { it to mimeType }
        }.toMap()
        
        /**
         * Get the MIME type for a given file extension.
         * @param extension The file extension (without the dot)
         * @return The corresponding MIME type, or UNKNOWN if not found
         */
        fun fromExtension(extension: String): MimeTypes {
            return extensionToMimeType[extension.lowercase()] ?: UNKNOWN
        }
        
        /**
         * Get the MIME type for a given filename.
         * @param filename The filename to check
         * @return The corresponding MIME type, or UNKNOWN if not found
         */
        fun fromFilename(filename: String): MimeTypes {
            val extension = filename.substringAfterLast('.', "").lowercase()
            return if (extension.isNotEmpty()) fromExtension(extension) else UNKNOWN
        }
        
        /**
         * Check if a MIME type is text-based.
         * @param mimeType The MIME type to check
         * @return true if the MIME type is text-based
         */
        fun isTextBased(mimeType: MimeTypes): Boolean {
            return mimeType.mimeType.startsWith("text/") ||
                   mimeType == APPLICATION_JSON ||
                   mimeType == APPLICATION_XML ||
                   mimeType == TEXT_JAVASCRIPT
        }
        
        /**
         * Check if a MIME type is compressible.
         * @param mimeType The MIME type to check
         * @return true if the MIME type can be compressed
         */
        fun isCompressible(mimeType: MimeTypes): Boolean {
            return isTextBased(mimeType) ||
                   mimeType.mimeType.startsWith("application/") ||
                   mimeType.mimeType.startsWith("image/") ||
                   mimeType.mimeType.startsWith("audio/") ||
                   mimeType.mimeType.startsWith("video/")
        }
    }
} 