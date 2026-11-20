package com.example.qa

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STAGE 6 & 8 — Robolectric GUI, E2E Workflow, Branding, Theme, and Accessibility Audit
 *
 * Launches the real MainActivity in Robolectric, navigates across all 5 main tabs, Settings pages,
 * Workstation modal, and Room Terminal, and checks branding consistency & touch target sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QaUiAndEndToEndRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `QA-UI-01 MainActivity boots cleanly and renders Dashboard header with rebranded title`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()

        val legacyHits = composeRule.onAllNodesWithText("NIFTY AI DESK", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
        assertTrue(
            "Legacy header 'NIFTY AI DESK' still present on Dashboard",
            legacyHits.isEmpty()
        )
        composeRule.onNodeWithText("NITHIX NIFTY DESK", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun `QA-UI-02 End-to-End 5-tab navigation flow renders every workspace without crashes`() {
        composeRule.waitForIdle()

        val expectedTags = listOf(
            0 to "dashboard_screen",
            1 to "multi_agent_desk_screen",
            2 to "options_chain_screen",
            3 to "execution_desk_screen",
            4 to "report_and_audit_screen"
        )

        for ((idx, screenTag) in expectedTags) {
            composeRule.onNodeWithTag("nav_tab_$idx").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(screenTag).assertIsDisplayed()
        }

        // Verify Options -> Futures sub-tab toggle works
        composeRule.onNodeWithTag("nav_tab_2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("futures_sub_tab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("options_sub_tab").performClick()
        composeRule.waitForIdle()

        // Return to Dashboard
        composeRule.onNodeWithTag("nav_tab_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
    }

    @Test
    fun `QA-UI-03 Settings navigation into Connectors and LLM & Theme sub-pages works end-to-end`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Connectors
        composeRule.onNodeWithTag("settings_connectors_item").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connectors_screen").assertIsDisplayed()

        // Navigate back to Settings Root
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open LLM & Theme page
        composeRule.onNodeWithTag("settings_llm_theme_card").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_llm_and_theme_page").assertIsDisplayed()

        // Switch theme to Institutional Light and exit Settings
        composeRule.onNodeWithTag("theme_option_institutional_light").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
    }

    @Test
    fun `QA-UI-04 Fullscreen Chart Workstation and Room OHLCV Terminal open and close back to Dashboard`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("expand_workstation_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chart_workstation_screen").assertIsDisplayed()

        // Switch inspector tabs inside workstation
        composeRule.onNodeWithTag("workstation_tab_indicators").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workstation_tab_risk").performClick()
        composeRule.waitForIdle()

        // Exit workstation
        composeRule.onNodeWithTag("workstation_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

        // Open Room OHLCV Terminal and close back
        composeRule.onNodeWithTag("open_ohlcv_terminal_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nifty_chart_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nifty_chart_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
    }

    @Test
    fun `QA-UI-05 BUG-UI-02 Workstation control chips satisfy Material 3 48dp minimum interactive height`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("expand_workstation_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chart_workstation_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("workstation_tf_1m").assertHeightIsAtLeast(48.dp)
    }
}
