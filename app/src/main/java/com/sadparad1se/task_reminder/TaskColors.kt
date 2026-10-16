package com.sadparad1se.task_reminder

import androidx.compose.ui.graphics.Color

/** Returns the fixed filter color for a time bucket button. */
internal fun TaskTimeBucket.filterColor(): Color {
    return when (this) {
        TaskTimeBucket.UNDATED -> Color(0xFF607D8B)
        TaskTimeBucket.UPCOMING -> Color(0xFFFFC107)
        TaskTimeBucket.CURRENT -> Color(0xFF2E7D32)
        TaskTimeBucket.OVERDUE -> Color(0xFFC62828)
    }
}

/** Parses a TaskNotes hex color string into a Compose color. */
internal fun parseTaskNotesColor(hexColor: String?): Color? {
    val color = hexColor
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 || it.length == 8 }
        ?.takeIf { value -> value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } }
        ?: return null
    val argb = if (color.length == 6) "FF$color" else color
    return Color(argb.toULong(16).toLong().toInt())
}
