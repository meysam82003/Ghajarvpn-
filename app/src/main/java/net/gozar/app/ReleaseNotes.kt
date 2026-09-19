package net.gozar.app

/**
 * A GitHub release body, turned into something the update dialog can lay out.
 *
 * The body is whatever Markdown the release was written in, and it was being
 * shown as one flat bulleted list - which flattened headings into bullets and
 * turned a Markdown table into a row of pipe characters. This parses the three
 * shapes a release note actually uses and leaves everything else as plain
 * text, so an unparsed line is still readable rather than dropped.
 */
sealed interface NoteBlock {
    /** A `#`-prefixed heading. */
    data class Heading(val text: String) : NoteBlock

    /** A `-`, `*` or `•` bullet. */
    data class Bullet(val text: String) : NoteBlock

    /**
     * A Markdown pipe table.
     *
     * [header] is null when the table had no header row. Rows are kept ragged
     * rather than padded, because a row with fewer cells than the header is a
     * fact about the note, not something to invent cells for.
     */
    data class Table(val header: List<String>?, val rows: List<List<String>>) : NoteBlock

    data class Paragraph(val text: String) : NoteBlock
}

object ReleaseNotes {

    /** Markdown's alignment row: `|---|:--:|` and every variant of it. */
    private val DIVIDER = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

    fun parse(body: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        val lines = body.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.isEmpty() -> i++

                line.startsWith("#") -> {
                    blocks += NoteBlock.Heading(line.trimStart('#', ' ').trim())
                    i++
                }

                isTableRow(line) -> {
                    // Consume the whole table: rows until a line that is not
                    // one. The divider decides whether the first row was a
                    // header or just the first row of data.
                    val collected = mutableListOf<List<String>>()
                    var sawDivider = false
                    while (i < lines.size) {
                        val l = lines[i].trim()
                        if (!isTableRow(l)) break
                        if (DIVIDER.matches(l)) sawDivider = true else collected += cells(l)
                        i++
                    }
                    if (collected.isNotEmpty()) {
                        blocks += if (sawDivider && collected.size > 1) {
                            NoteBlock.Table(collected.first(), collected.drop(1))
                        } else {
                            NoteBlock.Table(null, collected)
                        }
                    }
                }

                line.startsWith("-") || line.startsWith("*") || line.startsWith("•") -> {
                    blocks += NoteBlock.Bullet(line.trimStart('-', '*', '•', ' ').trim())
                    i++
                }

                else -> {
                    blocks += NoteBlock.Paragraph(line)
                    i++
                }
            }
        }
        return blocks
    }

    /**
     * A table row has a pipe and is not a link or a code fence that happens to
     * contain one - so it must start with a pipe, which every table in a
     * release note written for this dialog does.
     */
    private fun isTableRow(line: String): Boolean =
        line.startsWith("|") && line.indexOf('|', 1) >= 0

    private fun cells(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }
}
