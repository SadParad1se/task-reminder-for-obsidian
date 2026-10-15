package com.sadparad1se.task_reminder

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private fun checkFooter(darkTheme: Boolean, fontScale: Float) {
        val tasks = listOf(
            task("Long metadata").copy(
                status = "In progress with a long status name",
                tags = listOf("projects/very-long-unbroken-tag-name-that-must-wrap", "review", "work")
            ),
            task("No metadata")
        )
        val notified = mutableListOf<String>()
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
                            onNotifyTask = { notified.add(it.title) },
                            onRescan = {},
                            canRescan = true,
                            isScanning = false,
                            onOpenSettings = {}
                        )
                    }
                }
            }

            tasks.forEach { task ->
                compose.onNodeWithContentDescription("Create notification for ${task.title}")
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp)
                    .performTouchInput { click() }
            }
            compose.runOnIdle { assertEquals(tasks.map { it.title }, notified) }
        }
    }

    private fun task(title: String) = TaskListItem(
        title = title,
        scheduled = null,
        due = null,
        excerpt = ""
    )
}
