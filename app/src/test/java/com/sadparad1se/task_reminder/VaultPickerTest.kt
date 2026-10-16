package com.sadparad1se.task_reminder

import android.app.Activity
import android.content.Intent
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultPickerTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private val context = RuntimeEnvironment.getApplication()
    private val uri = DocumentsContract.buildTreeDocumentUri("vault.picker", "root")
    private val selectedVaults = mutableStateOf(emptyList<String>())
    private val selections = mutableListOf<String>()
    private var requestCode = 0
    private lateinit var pickerIntent: Intent
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this@VaultPickerTest.requestCode = requestCode
            pickerIntent = input as Intent
        }
    }
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }

    @Test
    fun canceledAndMissingSelectionsDoNotInvokeTheCallback() = withPicker {
        select(Activity.RESULT_CANCELED, Intent().setData(uri))
        select(Activity.RESULT_OK, null)
        assertTrue(selections.isEmpty())
    }

    @Test
    fun duplicateSelectionUsesTheLatestSelectedVaults() = withPicker {
        compose.runOnIdle { selectedVaults.value = listOf(uri.toString()) }
        select(Activity.RESULT_OK, Intent().setData(uri))
        // No provider is registered: a duplicate must be skipped before vault validation.
        assertTrue(selections.isEmpty())
    }

    @Test
    fun invalidVaultExplainsTheProblemWithoutInvokingTheCallback() = withPicker {
        registerVault(valid = false)
        select(Activity.RESULT_OK, Intent().setData(uri))
        assertTrue(selections.isEmpty())
        assertEquals(InvalidObsidianVaultMessage, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun validVaultInvokesTheScanCallbackAndPickerRequestsOnlyReadAccess() = withPicker {
        registerVault(valid = true)
        select(Activity.RESULT_OK, Intent().setData(uri))
        assertEquals(listOf(uri.toString()), selections)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, pickerIntent.action)
        assertTrue(pickerIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, pickerIntent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(pickerIntent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    }

    private fun withPicker(check: () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            controller.get().setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    TaskReminderTheme {
                        val chooseVault = rememberVaultPicker(selectedVaults.value) { selections.add(it) }
                        Button(onClick = chooseVault) { Text("Choose vault") }
                    }
                }
            }
            check()
        }
    }

    private fun select(resultCode: Int, data: Intent?) {
        compose.onNodeWithText("Choose vault").performClick()
        compose.runOnIdle { registry.dispatchResult(requestCode, resultCode, data) }
        compose.waitForIdle()
    }

    private fun registerVault(valid: Boolean) {
        val root = File(context.cacheDir, "picker-vault").apply { mkdirs() }
        if (valid) File(root, ".obsidian").mkdirs()
        val provider = TestVaultDocumentsProvider(root)
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "vault.picker"
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal("vault.picker", provider)
    }
}
