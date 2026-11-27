package com.nexus.player.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Repro for "none of the buttons work" on a phone: touch tap on a HudButton must fire onClick. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HudTouchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun iconLabelButtonTapFiresOnClick() {
        var clicks = 0
        rule.setContent { HudButton({ clicks++ }, icon = HudIcons.Sound, label = "Sound") }
        rule.onNodeWithText("Sound").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test fun touchTapFiresOnClick() {
        var clicks = 0
        rule.setContent { HudButton({ clicks++ }) { Text("Tap me") } }
        rule.onNodeWithText("Tap me").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dpadCenterStillFiresOnceAndDisabledIgnoresTouch() {
        var clicks = 0
        var disabledClicks = 0
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                HudButton({ clicks++ }) { Text("Focus me") }
                HudButton({ disabledClicks++ }, enabled = false) { Text("Disabled") }
            }
        }
        rule.onNodeWithText("Focus me").requestFocus()
        rule.onNodeWithText("Focus me").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Disabled").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertEquals(0, disabledClicks)
    }
}
