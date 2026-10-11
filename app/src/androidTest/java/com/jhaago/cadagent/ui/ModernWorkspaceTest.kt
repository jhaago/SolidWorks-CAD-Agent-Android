package com.jhaago.cadagent.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.live.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.RemoteUiState
import com.jhaago.cadagent.remote.ui.components.AiTaskPanel
import com.jhaago.cadagent.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModernWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun closeScope() { scope.cancel() }

    private fun container() = AppContainer().also { container ->
        container.configureRemoteSettings(scope, object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse = error("UI fixtures must not contact Windows")
        }, object : RemoteCredentialStore {
            override fun read(endpoint: RemoteEndpoint): PairedWorkstation? = null
            override fun write(workstation: PairedWorkstation) = Unit
            override fun delete(endpoint: RemoteEndpoint) = Unit
        })
    }

    private val review = RemoteUiState(session = RemoteWorkstationStatus(isLive = true, task = AiTaskState(
        id = "fixture-job", instruction = "Create a 100 x 60 x 10 mm plate with a centred 20 mm through-hole.",
        phase = AiTaskPhase.AwaitingApproval, revisionId = "fixture-revision", revisionNumber = 2, planVersion = 1,
        summary = "Rectangular plate with a centred through-hole", planValidated = true,
        message = "Review the current plan before execution.",
        proposedCommands = listOf("Create a new part", "Sketch a 100 x 60 mm rectangle on the Top Plane", "Extrude the plate to 10 mm", "Cut a centred 20 mm through-hole"))))

    private fun capture(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-captures").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun unpairedChatProvidesADirectRouteToPairingSettings() {
        compose.setContent { CadAgentApp(container()) }
        capture("android-chat")
        compose.onNodeWithTag("open-pairing-settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("cad-chat-screen").assertDoesNotExist()
        compose.onNodeWithText("Workstation pairing").assertIsDisplayed()
        compose.onNodeWithTag("workstation-endpoint").assertIsDisplayed()
        capture("android-settings")
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithTag("unpaired-workstation").assertIsDisplayed()
        capture("android-remote")
    }

    @Test fun approvalRemainsReachableWithLargeTextAndRequiresExplicitClick() {
        var approvals = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CadAgentTheme { Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        AiTaskPanel(review, {}, {}, onApprove = { approvals++ })
                    }
                } }
            }
        }
        assertEquals(0, approvals)
        compose.onNodeWithTag("approve-cad-plan").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("android-plan-large-text")
        compose.onNodeWithTag("approve-cad-plan").performClick()
        compose.runOnIdle { assertEquals(1, approvals) }
    }

    @Test fun revisionDraftRestoresOnlyForTheSameReviewRevision() {
        val visible = mutableStateOf(true)
        val currentReview = mutableStateOf(review)
        compose.setContent { CadAgentTheme {
            val holder = rememberSaveableStateHolder()
            if (visible.value) {
                holder.SaveableStateProvider("review") {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        AiTaskPanel(currentReview.value, {}, {})
                    }
                }
            }
        } }
        compose.onNodeWithTag("revision-instructions").performScrollTo()
        compose.onNodeWithTag("revision-instructions").performTextInput("Increase the plate thickness to 12 mm")
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("revision-instructions").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("revision-instructions").assertTextContains("Increase the plate thickness to 12 mm")
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("revision-instructions").assertDoesNotExist()
        compose.runOnIdle {
            currentReview.value = review.copy(session = review.session.copy(
                task = review.session.task.copy(revisionId = "next-revision", revisionNumber = 2)))
            visible.value = true
        }
        compose.onNodeWithTag("revision-instructions").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("revise-cad-plan").assertIsNotEnabled()
    }
}
