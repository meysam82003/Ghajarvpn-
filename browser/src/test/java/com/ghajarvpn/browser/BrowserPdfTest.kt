package com.ghajarvpn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPdfTest {

    @Test
    fun `file names are deterministic, sanitized and prefixed with url digest`() {
        val a = BrowserPdf.fileNameFor("https://example.com/files/Report 2024.pdf")
        val b = BrowserPdf.fileNameFor("https://example.com/files/Report 2024.pdf")
        assertEquals(a, b)
        assertTrue(a.startsWith("pdf-"))
        assertTrue(!a.contains(' '))
        assertTrue(a.endsWith(".pdf"))
    }

    @Test
    fun `urls without pdf name get a default document name`() {
        val name = BrowserPdf.fileNameFor("https://example.com/download?id=42")
        assertTrue(name.endsWith("document.pdf"))
        assertFalse(name.contains('?'))
    }

    @Test
    fun `path traversal fragments are stripped`() {
        val name = BrowserPdf.fileNameFor("https://example.com/../../etc/passwd.pdf")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(".."))
    }
}
