package com.sadparad1se.task_reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Parsed TaskNotes fields read from a Markdown file before vault and database metadata are attached. */
data class ParsedTaskNote(
    val title: String,
    val excerpt: String,
    val status: String?,
    val priority: String?,
    val scheduled: String?,
    val due: String?,
    val dateCreated: String?,
    val dateModified: String?,
    val tags: List<String>
)

/** Parses TaskNotes-compatible Markdown notes into task metadata used by the scanner. */
class MarkdownTaskParser {
    /** Returns a parsed task note, or null when the Markdown file is not a TaskNotes task. */
    fun parseTaskNote(
        markdown: String,
        fileName: String?
    ): ParsedTaskNote? {
        val lines = markdown.lineSequence().toList()
        val frontmatter = readYamlFrontmatter(lines) ?: return null
        val properties = frontmatter.lines.mapNotNull { parseYamlScalarProperty(it) }.toMap()
        val tags = parseTaskTags(frontmatter.lines)
        if (tags.none { it.equals(TaskTag, ignoreCase = true) }) return null

        val body = parseTitleAndExcerpt(
            bodyLines = lines.drop(frontmatter.closingLineIndex + 1),
            fileName = fileName
        )

        return ParsedTaskNote(
            title = body.title,
            excerpt = body.excerpt,
            status = properties[StatusProperty],
            priority = properties[PriorityProperty],
            scheduled = properties[ScheduledProperty]?.let { parseDateOrDateTime(it) },
            due = properties[DueProperty]?.let { parseDateOrDateTime(it) },
            dateCreated = properties[DateCreatedProperty],
            dateModified = properties[DateModifiedProperty],
            tags = tags
        )
    }

    /** Reads the YAML frontmatter block when the file starts with opening and closing delimiters. */
    private fun readYamlFrontmatter(lines: List<String>): Frontmatter? {
        if (lines.firstOrNull()?.trim() != "---") return null
        val frontmatterEnd = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (frontmatterEnd < 0) return null

        val closingLineIndex = frontmatterEnd + 1
        return Frontmatter(
            lines = lines.subList(1, closingLineIndex),
            closingLineIndex = closingLineIndex
        )
    }

    /** Extracts the display title and first body excerpt from Markdown body lines. */
    private fun parseTitleAndExcerpt(
        bodyLines: List<String>,
        fileName: String?
    ): ParsedBody {
        val titleMatch = bodyLines
            .withIndex()
            .firstNotNullOfOrNull { (index, line) ->
                headingRegex.matchEntire(line.trim())?.groupValues?.getOrNull(1)?.trim()?.let { title ->
                    index to title
                }
            }
        val title = titleMatch?.second
            ?: fileName?.removeSuffix(".md")?.takeIf { it.isNotBlank() }
            ?: "Untitled note"
        val excerptStartIndex = titleMatch?.let { it.first + 1 } ?: 0
        val excerpt = bodyLines
            .drop(excerptStartIndex)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && headingRegex.matchEntire(it) == null }
            ?.shorten()
            ?: ""

        return ParsedBody(title, excerpt)
    }

    /** Parses one simple `key: value` YAML frontmatter line. */
    private fun parseYamlScalarProperty(line: String): Pair<String, String>? {
        val match = propertyRegex.matchEntire(line.trim()) ?: return null
        val value = match.groupValues[2]
            .trim()
            .trim('"', '\'')
            .takeIf { it.isNotBlank() }
            ?: return null
        return match.groupValues[1] to value
    }

    /** Normalizes an ISO local date string, or returns null when invalid. */
    private fun parseDate(value: String): String? {
        return runCatching {
            DateTimeFormatter.ISO_LOCAL_DATE.format(
                LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            )
        }.getOrNull()
    }

    /** Accepts TaskNotes scheduled values as either ISO dates, local datetimes, or offset datetimes. */
    private fun parseDateOrDateTime(value: String): String? {
        parseDate(value)?.let { return it }
        return runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            value
        }.recoverCatching {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            value
        }.getOrNull()
    }

    /** Parses inline or multiline YAML tags from frontmatter. */
    private fun parseTaskTags(frontmatterLines: List<String>): List<String> {
        val inlineTags = frontmatterLines
            .firstNotNullOfOrNull { parseYamlScalarProperty(it)?.takeIf { (key, _) -> key == TagsProperty }?.second }
        if (inlineTags != null) {
            return parseInlineTags(inlineTags)
        }

        val tagsLineIndex = frontmatterLines.indexOfFirst { it.trim() == "$TagsProperty:" }
        if (tagsLineIndex < 0) return emptyList()

        return frontmatterLines
            .drop(tagsLineIndex + 1)
            .takeWhile { line ->
                val trimmed = line.trim()
                trimmed.isBlank() || trimmed.startsWith("- ") || trimmed.startsWith("* ")
            }
            .mapNotNull { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .trim()
                    .trim('"', '\'')
                    .takeIf { it.isNotBlank() }
            }
            .distinct()
    }

    /** Parses a scalar tag value or bracketed YAML tag list. */
    private fun parseInlineTags(value: String): List<String> {
        val trimmedValue = value.trim()
        if (!trimmedValue.startsWith("[") || !trimmedValue.endsWith("]")) {
            return trimmedValue.takeIf { it.isNotBlank() }?.let { listOf(it.trim('"', '\'')) } ?: emptyList()
        }
        return trimmedValue
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .mapNotNull { tag ->
                tag.trim()
                    .trim('"', '\'')
                    .takeIf { it.isNotBlank() }
            }
            .distinct()
    }

    /** Shortens long excerpts to a single compact preview string. */
    private fun String.shorten(): String {
        return if (length <= MaxExcerptLength) this else take(MaxExcerptLength - 3).trimEnd() + "..."
    }

    private companion object {
        const val DueProperty = "due"
        const val ScheduledProperty = "scheduled"
        const val StatusProperty = "status"
        const val PriorityProperty = "priority"
        const val DateCreatedProperty = "dateCreated"
        const val DateModifiedProperty = "dateModified"
        const val TagsProperty = "tags"
        const val TaskTag = "task"
        const val MaxExcerptLength = 140
        val headingRegex = Regex("^#{1,6}\\s+(.+)$")
        val propertyRegex = Regex("^([A-Za-z0-9_-]+)\\s*:\\s*(.+)$")
    }

    /** YAML frontmatter content and the line index where it closes. */
    private data class Frontmatter(
        val lines: List<String>,
        val closingLineIndex: Int
    )

    /** Display title and excerpt extracted from the Markdown body. */
    private data class ParsedBody(
        val title: String,
        val excerpt: String
    )
}
