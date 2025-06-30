package borg.trikeshed.net.http

import kotlin.test.*

class MimeTypeTest {

    @Test
    fun testCommonImageTypes() {
        assertEquals("image/jpeg", MimeType.jpg.contentType)
        assertEquals("image/jpeg", MimeType.jpeg.contentType)
        assertEquals("image/jpeg", MimeType.jpe.contentType)
        assertEquals("image/png", MimeType.png.contentType)
        assertEquals("image/gif", MimeType.gif.contentType)
        assertEquals("image/svg+xml", MimeType.svg.contentType)
    }

    @Test
    fun testCommonTextTypes() {
        assertEquals("text/plain", MimeType.txt.contentType)
        assertEquals("text/html", MimeType.html.contentType)
        assertEquals("text/html", MimeType.htm.contentType)
        assertEquals("text/css", MimeType.css.contentType)
        assertEquals("text/csv", MimeType.csv.contentType)
        assertEquals("application/javascript", MimeType.js.contentType)
        assertEquals("application/json", MimeType.json.contentType)
    }

    @Test
    fun testCommonDocumentTypes() {
        assertEquals("application/pdf", MimeType.pdf.contentType)
        assertEquals("application/msword", MimeType.doc.contentType)
        assertEquals("application/vnd.ms-excel", MimeType.xls.contentType)
        assertEquals("application/vnd.ms-powerpoint", MimeType.ppt.contentType)
    }

    @Test
    fun testCommonAudioTypes() {
        assertEquals("audio/mpeg", MimeType.mp3.contentType)
        assertEquals("audio/wav", MimeType.wav.contentType)
        assertEquals("audio/x-aiff", MimeType.aiff.contentType)
        assertEquals("audio/x-aiff", MimeType.aif.contentType)
        assertEquals("audio/x-aiff", MimeType.aifc.contentType)
    }

    @Test
    fun testCommonVideoTypes() {
        assertEquals("video/mp4", MimeType.mp4.contentType)
        assertEquals("video/x-msvideo", MimeType.avi.contentType)
        assertEquals("video/quicktime", MimeType.mov.contentType)
    }

    @Test
    fun testCommonArchiveTypes() {
        assertEquals("application/zip", MimeType.zip.contentType)
        assertEquals("application/x-rar-compressed", MimeType.rar.contentType)
        assertEquals("application/x-tar", MimeType.tar.contentType)
    }

    @Test
    fun testCommonCodeTypes() {
        assertEquals("text/x-java-source", MimeType.java.contentType)
        assertEquals("text/x-csrc", MimeType.c.contentType)
        assertEquals("text/x-c++src", MimeType.cpp.contentType)
        assertEquals("text/x-c++src", MimeType.cc.contentType)
        assertEquals("text/x-c++src", MimeType.cxx.contentType)
        assertEquals("text/x-c++hdr", MimeType.hpp.contentType)
        assertEquals("text/x-c++hdr", MimeType.hh.contentType)
        assertEquals("text/x-c++hdr", MimeType.hxx.contentType)
        assertEquals("text/x-python", MimeType.py.contentType)
        assertEquals("text/x-php", MimeType.php.contentType)
    }

    @Test
    fun testCommonDataTypes() {
        assertEquals("application/xml", MimeType.xml.contentType)
        assertEquals("text/xml", MimeType.xml.contentType)
        assertEquals("application/json", MimeType.json.contentType)
        assertEquals("text/csv", MimeType.csv.contentType)
    }

    @Test
    fun testSpecialCharactersInNames() {
        // Test enum entries with special characters (quoted with backticks)
        assertEquals("application/mac-compactpro", MimeType.`$cpt`.contentType)
        assertEquals("chemical/x-ncbi-asn1-ascii", MimeType.`$ent`.contentType)
    }

    @Test
    fun testAllEnumValuesHaveContentType() {
        // Verify that all enum values have a non-empty content type
        MimeType.values().forEach { mimeType ->
            assertTrue(mimeType.contentType.isNotEmpty(), "MimeType ${mimeType.name} has empty content type")
            assertTrue(mimeType.contentType.contains("/"), "MimeType ${mimeType.name} content type should contain '/'")
        }
    }

    @Test
    fun testContentTypeFormat() {
        // Verify that content types follow the standard format: type/subtype
        MimeType.values().forEach { mimeType ->
            val parts = mimeType.contentType.split("/")
            assertEquals(2, parts.size, "MimeType ${mimeType.name} should have exactly one '/'")
            assertTrue(parts[0].isNotEmpty(), "MimeType ${mimeType.name} type part should not be empty")
            assertTrue(parts[1].isNotEmpty(), "MimeType ${mimeType.name} subtype part should not be empty")
        }
    }

    @Test
    fun testCommonWebTypes() {
        assertEquals("text/html", MimeType.html.contentType)
        assertEquals("text/css", MimeType.css.contentType)
        assertEquals("application/javascript", MimeType.js.contentType)
        assertEquals("image/x-icon", MimeType.ico.contentType)
    }

    @Test
    fun testExecutableTypes() {
        assertEquals("application/x-msdos-program", MimeType.exe.contentType)
        assertEquals("application/x-msdos-program", MimeType.com.contentType)
        assertEquals("application/x-msdos-program", MimeType.bat.contentType)
        assertEquals("application/x-msdos-program", MimeType.dll.contentType)
    }

    @Test
    fun testChemicalTypes() {
        assertEquals("chemical/x-pdb", MimeType.pdb.contentType)
        assertEquals("chemical/x-mol2", MimeType.mol2.contentType)
        assertEquals("chemical/x-xyz", MimeType.xyz.contentType)
        assertEquals("chemical/x-cml", MimeType.cml.contentType)
        assertEquals("chemical/x-cdx", MimeType.cdx.contentType)
    }
} 