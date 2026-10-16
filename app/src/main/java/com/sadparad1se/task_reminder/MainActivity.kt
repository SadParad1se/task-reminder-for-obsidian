package com.sadparad1se.task_reminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Builds the Compose root and routes between onboarding and the home task list. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { TaskReminderApp() }
        handleTaskNotesOpenIntent(intent, isRestored = savedInstanceState != null)
    }

    /** Handles new intents delivered while the existing activity instance is reused. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTaskNotesOpenIntent(intent)
    }

    /** Opens the task file when a notification launches the app with vault metadata. */
    private fun handleTaskNotesOpenIntent(intent: Intent, isRestored: Boolean = false) {
        val target = ObsidianProjectOpener.consumeTaskOpenTarget(intent, isRestored)
        setIntent(intent)
        if (target == null) return

        lifecycleScope.launch {
            ObsidianProjectOpener.openTaskNotes(this@MainActivity, target, showToast = false)
        }
    }
}
