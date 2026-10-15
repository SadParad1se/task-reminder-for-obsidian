package com.sadparad1se.task_reminder

/** A completed vault scan and its activity evidence, independent of the active-task database. */
data class VaultScanResult(
    val succeeded: Boolean,
    val scanStartedAt: Long,
    val taskActivityFound: Boolean = false
)

/** Checks both dates before completed tasks are filtered out of the displayed task collection. */
internal fun ParsedTaskNote.wasUpdatedBetween(previousScanAt: Long, scanStartedAt: Long, fileModifiedAt: Long?): Boolean {
    return listOfNotNull(dateModified?.toTaskNotesEpochMillisOrNull(), fileModifiedAt)
        .any { it > previousScanAt && it <= scanStartedAt }
}
