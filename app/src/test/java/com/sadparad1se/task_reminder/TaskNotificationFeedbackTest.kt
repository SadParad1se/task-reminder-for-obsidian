package com.sadparad1se.task_reminder

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskNotificationFeedbackTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearToasts() = ShadowToast.reset()

    @Test
    fun successfulPostsSignalTheirTaskEveryTimeWithoutShowingAToast() {
        val posted = mutableListOf<String>()
        repeat(2) { context.showTaskNotificationFeedback("task-a", TaskNotificationResult.POSTED, posted::add) }
        context.showTaskNotificationFeedback("task-b", TaskNotificationResult.POSTED, posted::add)
        assertEquals(listOf("task-a", "task-a", "task-b"), posted)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun blockedOrMissingTasksKeepErrorMessagesWithoutSuccessFeedback() {
        val posted = mutableListOf<String>()
        context.showTaskNotificationFeedback("task", TaskNotificationResult.DISABLED, posted::add)
        assertEquals(context.getString(R.string.task_notifications_disabled), ShadowToast.getTextOfLatestToast())
        context.showTaskNotificationFeedback("task", TaskNotificationResult.TASK_NOT_FOUND, posted::add)
        assertEquals(context.getString(R.string.task_notification_missing), ShadowToast.getTextOfLatestToast())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun retryOnlySignalsSuccessOncePostingIsAllowed() {
        val posted = mutableListOf<String>()
        context.showTaskNotificationFeedback("pending-task", TaskNotificationResult.DISABLED, posted::add)
        assertTrue(posted.isEmpty())
        ShadowToast.reset()
        context.showTaskNotificationFeedback("pending-task", TaskNotificationResult.POSTED, posted::add)
        assertEquals(listOf("pending-task"), posted)
        assertEquals(0, ShadowToast.shownToastCount())
    }
}
