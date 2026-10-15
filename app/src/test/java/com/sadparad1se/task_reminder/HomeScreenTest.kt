package com.sadparad1se.task_reminder

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun bellPostsForItsTaskWithoutOpeningObsidianAndCardStillOpensNote() {
        val calls = mutableListOf<String>()
        val task = task("Review plans").copy(status = "Open", tags = listOf("work"))
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                TaskReminderTheme {
                    HomeScreen(
                        tasks = listOf(task),
                        priorities = emptyList(),
                        priorityColors = emptyMap(),
                        excludedPriorities = emptySet(),
                        includedTimeBuckets = TaskTimeBucket.entries.toSet(),
                        onPriorityFilterToggle = {},
                        onTimeBucketToggle = {},
                        onTaskClick = { calls.add("open:${it.title}") },
                        onNotifyTask = { calls.add("notify:${it.title}") },
                        onRescan = {},
                        canRescan = true,
                        isScanning = false,
                        onOpenSettings = {}
                    )
                }
            }

            val bell = compose.onNodeWithContentDescription("Create notification for Review plans")
            bell
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .performTouchInput { click() }
            compose.runOnIdle { assertEquals(listOf("notify:Review plans"), calls) }
            assertEquals(
                bell.fetchSemanticsNode().boundsInRoot.center.y,
                compose.onNodeWithText("Open", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y,
                1f
            )

            compose.onNodeWithText(task.title).performTouchInput { click() }
            compose.runOnIdle { assertEquals(listOf("notify:Review plans", "open:Review plans"), calls) }
        }
    }

    @Test
    fun bellRemainsVisibleWithLongMetadataAndLargeTextInLightTheme() {
        checkFooter(darkTheme = false, fontScale = 2f)
    }

    @Test
    fun bellRemainsVisibleWithLongMetadataAndLargeTextInDarkTheme() {
        checkFooter(darkTheme = true, fontScale = 2f)
    }

    @Test
    fun successAnimatesTheMatchingBellAndRepeatedSuccessRestartsWithoutReplayingOnReturn() {
        val first = task("First task")
        val second = task("Second task")
        var visibleTasks by mutableStateOf(listOf(first, second))
        val successes = mutableStateMapOf<String, Long>()
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                TaskReminderTheme {
                    HomeScreen(
                        tasks = visibleTasks,
                        priorities = emptyList(), priorityColors = emptyMap(), excludedPriorities = emptySet(),
                        includedTimeBuckets = TaskTimeBucket.entries.toSet(),
                        onPriorityFilterToggle = {}, onTimeBucketToggle = {}, onTaskClick = {},
                        onNotifyTask = {}, onRescan = {}, canRescan = true, isScanning = false, onOpenSettings = {},
                        notificationSuccessCounts = successes.toMap()
                    )
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val firstBell = compose.onNodeWithContentDescription("Create notification for ${first.title}")
            val secondBell = compose.onNodeWithContentDescription("Create notification for ${second.title}")
            val resting = captureScreen(controller.get())

            // A tap alone does not claim success while posting or a permission request is pending.
            firstBell.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(128)
            firstBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

            compose.runOnIdle {
                successes[first.fileUri] = 1L
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(128)
            firstBell.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Notification created."))
            secondBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            assertFalse(resting.sameAs(captureScreen(controller.get())))

            compose.runOnIdle {
                successes[second.fileUri] = 1L
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(64)
            secondBell.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Notification created."))
            firstBell.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Notification created."))
            compose.runOnIdle {
                successes[first.fileUri] = 2L
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(128)
            firstBell.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Notification created."))
            compose.mainClock.advanceTimeBy(800)
            firstBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            assertTrue(resting.sameAs(captureScreen(controller.get())))

            // Stable task identity and a freshly composed row must not reuse another task's pulse.
            compose.runOnIdle {
                visibleTasks = listOf(second)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            secondBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            compose.runOnIdle {
                visibleTasks = listOf(second, first)
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(128)
            firstBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            secondBell.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    private fun checkFooter(darkTheme: Boolean, fontScale: Float) {
        val tasks = listOf(
            task("Long metadata").copy(
                status = "In progress with a long status name",
                tags = listOf("projects/very-long-unbroken-tag-name-that-must-wrap", "review", "work")
            ),
            task("No metadata")
        )
        val notified = mutableListOf<String>()
        val successes = mutableStateMapOf<String, Long>()
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    TaskReminderTheme(darkTheme = darkTheme) {
                        HomeScreen(
                            tasks = tasks,
                            priorities = emptyList(),
                            priorityColors = emptyMap(),
                            excludedPriorities = emptySet(),
                            includedTimeBuckets = TaskTimeBucket.entries.toSet(),
                            onPriorityFilterToggle = {},
                            onTimeBucketToggle = {},
                            onTaskClick = { throw AssertionError("Bell tap opened Obsidian") },
                            onNotifyTask = {
                                notified.add(it.title)
                                successes[it.fileUri] = (successes[it.fileUri] ?: 0L) + 1L
                            },
                            onRescan = {},
                            canRescan = true,
                            isScanning = false,
                            onOpenSettings = {},
                            notificationSuccessCounts = successes.toMap()
                        )
                    }
                }
            }

            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            tasks.forEachIndexed { index, task ->
                compose.onNodeWithContentDescription("Create notification for ${task.title}")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp)
                    .performTouchInput { click() }
                compose.mainClock.advanceTimeBy(128)
                compose.onNodeWithContentDescription("Create notification for ${task.title}")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Notification created."))
                if (index == 0) {
                    val file = File("build/reports/bell-feedback/${if (darkTheme) "dark" else "light"}.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { stream ->
                        captureScreen(controller.get())
                            .compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }
                compose.mainClock.advanceTimeBy(800)
            }
            compose.runOnIdle { assertEquals(tasks.map { it.title }, notified) }
        }
    }

    private fun captureScreen(activity: ComponentActivity): Bitmap = compose.runOnIdle {
        val view = activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    private fun task(title: String) = TaskListItem(
        title = title,
        scheduled = null,
        due = null,
        excerpt = "",
        fileUri = "content://tasks/$title"
    )
}
