package com.sadparad1se.task_reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Displays first-run guidance as a carousel with setup actions and progress dots. */
@Composable
fun OnboardingScreen(
    selectedVaultName: String?,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onChooseVault: (onVaultSelected: () -> Unit) -> Unit,
    onContinue: () -> Unit,
    onRequestNotifications: (onPermissionGranted: () -> Unit) -> Unit,
    onOpenExactAlarmSettings: (onPermissionGranted: () -> Unit) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPageCount })
    val scope = rememberCoroutineScope()
    val advanceToNextPage = {
        if (pagerState.currentPage < OnboardingPageCount - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }
    val pages = onboardingPages(
        selectedVaultName = selectedVaultName,
        hasNotificationPermission = hasNotificationPermission,
        canScheduleExactAlarms = canScheduleExactAlarms,
        onChooseVault = onChooseVault,
        onRequestNotifications = onRequestNotifications,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onOpenBatterySettings = onOpenBatterySettings,
        onSuccessfulAction = advanceToNextPage
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(onboardingBackgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageDots(
                    pageCount = pages.size,
                    selectedPage = pagerState.currentPage
                )
                OnboardingNavigation(
                    currentPage = pagerState.currentPage,
                    lastPage = pages.lastIndex,
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    onFinish = onContinue
                )
            }
        }
    }
}

/** Creates the immutable page descriptions rendered by the onboarding carousel. */
private fun onboardingPages(
    selectedVaultName: String?,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onChooseVault: (onVaultSelected: () -> Unit) -> Unit,
    onRequestNotifications: (onPermissionGranted: () -> Unit) -> Unit,
    onOpenExactAlarmSettings: (onPermissionGranted: () -> Unit) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSuccessfulAction: () -> Unit
): List<OnboardingPage> {
    return listOf(
        OnboardingPage(
            title = "Task Reminder for Obsidian",
            body = "This app reads TaskNotes tasks from your Obsidian vault and gives you push notifications.\nThe TaskNotes and Obsidian Advanced URI plugins are required.",
            footer = "This app is not affiliated with Obsidian or the TaskNotes plugin."
        ),
        OnboardingPage(
            title = "Your Data Stays Local",
            body = "The app reads the tasks and TaskNotes settings. Nothing is uploaded or sent anywhere, and your notes are never modified.",
            footer = ""
        ),
        OnboardingPage(
            title = "Choose Your Vault",
            body = "Choose the first Obsidian vault folder now.",
            status = selectedVaultName?.let { "Vault selected: $it" } ?: "No vault selected yet.",
            actionLabel = "Choose Obsidian vault",
            onAction = { onChooseVault(onSuccessfulAction) },
            footer = "You can choose or change a vault later in Settings."
        ),
        OnboardingPage(
            title = "Notifications",
            body = "Allow push notification permission to receive reminders.",
            status = if (hasNotificationPermission) "Notifications enabled." else "Notifications disabled.",
            actionLabel = "Allow notifications",
            onAction = { onRequestNotifications(onSuccessfulAction) },
            footer = "You can change this later in Settings."
        ),
        OnboardingPage(
            title = "Exact Reminder Times",
            body = "Exact alarm permission lets Android fire scheduled reminders at the intended time instead of delaying them.",
            status = if (canScheduleExactAlarms) "Exact alarms enabled." else "Exact alarms disabled.",
            actionLabel = "Allow exact alarms",
            onAction = { onOpenExactAlarmSettings(onSuccessfulAction) },
            footer = "You can change this later in Settings."
        ),
        OnboardingPage(
            title = "Background Reliability (optional)",
            body = "Android battery optimization can delay background vault scans and notifications.",
            status = "Unknown.",
            actionLabel = "Open battery settings",
            onAction = onOpenBatterySettings,
            footer = "You can change this later in Settings."
        ),
        OnboardingPage(
            title = "Ready",
            body = "That's all. Enjoy!",
            footer = "More options in the settings."
        )
    )
}

/** Displays title, body, status, and page-specific action for one carousel page. */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(86.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            page.status?.let { status ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            page.actionLabel?.let { actionLabel ->
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { page.onAction?.invoke() }) {
                    Text(actionLabel)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = page.footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Shows dot indicators for the current onboarding page. */
@Composable
private fun PageDots(
    pageCount: Int,
    selectedPage: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val color = if (index == selectedPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

/** Displays Back, Next, and final Get started controls for onboarding. */
@Composable
private fun OnboardingNavigation(
    currentPage: Int,
    lastPage: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onBack,
            enabled = currentPage > 0
        ) {
            Text("Back")
        }

        if (currentPage < lastPage) {
            Button(onClick = onNext) {
                Text("Next")
            }
        } else {
            Button(onClick = onFinish) {
                Text("Get started")
            }
        }
    }
}

/** Data needed to render one onboarding carousel page. */
private data class OnboardingPage(
    val title: String,
    val body: String,
    val footer: String,
    val status: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

private const val OnboardingPageCount = 7

/** Matches the logo's dark glow while staying readable in light and dark mode. */
@Composable
private fun onboardingBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.background
        )
    )
}
