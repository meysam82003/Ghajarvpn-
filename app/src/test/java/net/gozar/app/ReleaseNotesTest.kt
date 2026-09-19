package net.gozar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release body is written by hand on GitHub, so the parser has to survive
 * whatever a note actually contains - and the update dialog is the one screen
 * a user sees before deciding to install, so a dropped line there is a line
 * they never read.
 */
class ReleaseNotesTest {

    @Test
    fun `heading bullet and paragraph are told apart`() {
        val blocks = ReleaseNotes.parse(
            """
            ## New
            - a bullet
            * another bullet
            plain sentence
            """.trimIndent()
        )
        assertEquals(
            listOf(
                NoteBlock.Heading("New"),
                NoteBlock.Bullet("a bullet"),
                NoteBlock.Bullet("another bullet"),
                NoteBlock.Paragraph("plain sentence")
            ),
            blocks
        )
    }

    @Test
    fun `a table keeps its header and rows`() {
        val blocks = ReleaseNotes.parse(
            """
            | Feature | What it does |
            | --- | --- |
            | Doctor | Measures the connection |
            | Widget | Connects from the home screen |
            """.trimIndent()
        )
        assertEquals(1, blocks.size)
        val table = blocks.first() as NoteBlock.Table
        assertEquals(listOf("Feature", "What it does"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Doctor", "Measures the connection"), table.rows[0])
    }

    /** Alignment rows come in several spellings; none of them is a data row. */
    @Test
    fun `alignment dividers are not rows`() {
        val table = ReleaseNotes.parse(
            """
            | a | b |
            |:--|--:|
            | 1 | 2 |
            """.trimIndent()
        ).single() as NoteBlock.Table
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    /** Without a divider there is no header - the first row is data. */
    @Test
    fun `a table with no divider has no header`() {
        val table = ReleaseNotes.parse("| 1 | 2 |\n| 3 | 4 |").single() as NoteBlock.Table
        assertNull(table.header)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `a ragged row is kept as it was written`() {
        val table = ReleaseNotes.parse(
            """
            | a | b | c |
            | --- | --- | --- |
            | only one |
            """.trimIndent()
        ).single() as NoteBlock.Table
        assertEquals(listOf(listOf("only one")), table.rows)
    }

    @Test
    fun `text after a table is not swallowed by it`() {
        val blocks = ReleaseNotes.parse(
            """
            | a |
            | --- |
            | 1 |

            after the table
            """.trimIndent()
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is NoteBlock.Table)
        assertEquals(NoteBlock.Paragraph("after the table"), blocks[1])
    }

    /** A markdown link contains a pipe in no sane note, but a bullet that
     *  mentions one must not be mistaken for a table row. */
    @Test
    fun `a bullet containing a pipe stays a bullet`() {
        val blocks = ReleaseNotes.parse("- use a | b to split")
        assertEquals(listOf(NoteBlock.Bullet("use a | b to split")), blocks)
    }

    @Test
    fun `an empty body yields nothing`() {
        assertTrue(ReleaseNotes.parse("").isEmpty())
        assertTrue(ReleaseNotes.parse("\n\n   \n").isEmpty())
    }

    /** The real 1.0.1 note shape: two tables, headings, and a closing note. */
    @Test
    fun `the shipped note shape parses into its parts`() {
        val blocks = ReleaseNotes.parse(
            """
            ## A
            | x | y |
            | --- | --- |
            | 1 | 2 |
            ## B
            - a point
            """.trimIndent()
        )
        assertEquals(4, blocks.size)
        assertEquals(NoteBlock.Heading("A"), blocks[0])
        assertTrue(blocks[1] is NoteBlock.Table)
        assertEquals(NoteBlock.Heading("B"), blocks[2])
        assertEquals(NoteBlock.Bullet("a point"), blocks[3])
    }
}
