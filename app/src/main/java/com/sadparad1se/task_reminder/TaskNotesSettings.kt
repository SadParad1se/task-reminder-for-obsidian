package com.sadparad1se.task_reminder

import org.json.JSONObject

/** TaskNotes plugin settings needed to find task files and style active tasks. */
data class TaskNotesSettings(
    val tasksFolder: String,
    val completedStatuses: Set<String>,
    val statusColors: Map<String, String>,
    val priorityColors: Map<String, String>,
    val priorityWeights: Map<String, Int>
) {
    /** Returns true when the provided TaskNotes status is configured as completed. */
    fun isCompletedStatus(status: String?): Boolean {
        return status?.trim()?.lowercase() in completedStatuses
    }

    /** Returns the configured hex color for a TaskNotes status, or null when none is configured. */
    fun colorForStatus(status: String?): String? {
        return status?.trim()?.lowercase()?.let(statusColors::get)
    }

    /** Returns the configured hex color for a TaskNotes priority, or null when none is configured. */
    fun colorForPriority(priority: String?): String? {
        return priority?.trim()?.lowercase()?.let(priorityColors::get)
    }

    /** Returns the configured sort weight for a TaskNotes priority, or null when none is configured. */
    fun weightForPriority(priority: String?): Int? {
        return priority?.trim()?.lowercase()?.let(priorityWeights::get)
    }
}

/** Parses the subset of TaskNotes `data.json` settings used by this app. */
class TaskNotesSettingsParser {
    /** Reads the configured tasks folder, completed statuses, and display colors from TaskNotes JSON. */
    fun parse(json: String): TaskNotesSettings {
        val root = JSONObject(json)
        val tasksFolder = root.optString(TasksFolderKey)
            .trim()
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("TaskNotes tasksFolder is missing")
        val customStatuses = root.optJSONArray(CustomStatusesKey)
        val completedStatuses = buildSet {
            if (customStatuses == null) return@buildSet
            for (index in 0 until customStatuses.length()) {
                val status = customStatuses.optJSONObject(index) ?: continue
                if (!status.optBoolean(IsCompletedKey, false)) continue
                status.optString(ValueKey)
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }

        return TaskNotesSettings(
            tasksFolder = tasksFolder,
            completedStatuses = completedStatuses,
            statusColors = parseColorMap(root.optJSONArray(CustomStatusesKey)),
            priorityColors = parseColorMap(root.optJSONArray(CustomPrioritiesKey)),
            priorityWeights = parsePriorityWeights(root.optJSONArray(CustomPrioritiesKey))
        )
    }

    /** Returns a map from TaskNotes setting value to validated hex color. */
    private fun parseColorMap(items: org.json.JSONArray?): Map<String, String> {
        if (items == null) return emptyMap()

        return buildMap {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val value = item.optString(ValueKey)
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotBlank() }
                    ?: continue
                val color = item.optString(ColorKey)
                    .trim()
                    .takeIf { it.matches(HexColorRegex) }
                    ?: continue
                put(value, color)
            }
        }
    }

    /** Returns a map from TaskNotes priority value to its configured ordering weight. */
    private fun parsePriorityWeights(items: org.json.JSONArray?): Map<String, Int> {
        if (items == null) return emptyMap()

        return buildMap {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val value = item.optString(ValueKey)
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotBlank() }
                    ?: continue
                if (!item.has(WeightKey)) continue
                put(value, item.optInt(WeightKey))
            }
        }
    }

    private companion object {
        const val TasksFolderKey = "tasksFolder"
        const val CustomStatusesKey = "customStatuses"
        const val CustomPrioritiesKey = "customPriorities"
        const val IsCompletedKey = "isCompleted"
        const val ValueKey = "value"
        const val ColorKey = "color"
        const val WeightKey = "weight"
        val HexColorRegex = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    }
}
