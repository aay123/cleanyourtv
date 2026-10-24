package com.cleanyourtv.mobile.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebloatParserTest {

    @Test
    fun `comments and blank lines are ignored`() {
        val text = """
            # Header
            com.foo.one

              # indented comment
            com.foo.two
        """.trimIndent()

        assertEquals(listOf("com.foo.one", "com.foo.two"), DebloatParser.parse(text).map { it.name })
    }

    @Test
    fun `a trailing comment becomes the note`() {
        val packages = DebloatParser.parse("com.foo.one  # keep for HDMI\n")

        assertEquals("keep for HDMI", packages.single().note)
    }

    @Test
    fun `a BOM and CRLF are handled`() {
        val packages = DebloatParser.parse("\uFEFFcom.foo.one\r\ncom.foo.two\r\n")

        assertEquals(listOf("com.foo.one", "com.foo.two"), packages.map { it.name })
    }

    @Test
    fun `a repeated package keeps its first note`() {
        val packages = DebloatParser.parse("com.foo.one # first\ncom.foo.one # second\n")

        assertEquals(1, packages.size)
        assertEquals("first", packages.single().note)
    }

    @Test
    fun `a shell command stops the list`() {
        val error = assertThrows(DebloatFormatException::class.java) {
            DebloatParser.parse("com.foo.one\npm uninstall com.foo.two\n")
        }

        assertEquals(2, error.line)
        assertEquals("pm", error.token)
    }

    @Test
    fun `an empty list is refused`() {
        assertThrows(DebloatFormatException::class.java) {
            DebloatParser.parse("# nothing here\n\n")
        }
    }

    @Test
    fun `the header keeps only the leading comments`() {
        val text = """
            # First line
            # Second line

            # Not in the header
            com.foo.one
        """.trimIndent()

        assertEquals(listOf("First line", "Second line"), DebloatParser.header(text))
    }

    @Test
    fun `a package name needs at least two segments`() {
        assertTrue(DebloatParser.isPackageName("com.foo.bar"))
        assertTrue(!DebloatParser.isPackageName("single"))
    }
}
