package net.gozar.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateModalTest {
    @get:Rule val compose=createComposeRule()
    private fun outsideTap() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val t=SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach { action ->
            val event=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,2f,2f,0)
            try { automation.injectInputEvent(event,true) } finally { event.recycle() }
        }
        compose.waitForIdle()
    }
    @Test fun downloadRejectsBackAndOutsideAndOnlyCancelIsAction() {
        var dismissed=0;var cancelled=0
        compose.setContent { MaterialTheme { UpdateStageModal(1,"download","لغو دانلود",{dismissed++},{cancelled++}) { Text("payload") } } }
        Espresso.pressBack();outsideTap()
        compose.onNodeWithText("payload").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("لغو دانلود").performClick()
        compose.runOnIdle { assertEquals(0,dismissed);assertEquals(1,cancelled) }
    }
    @Test fun verifyRejectsBackOutsideAndConfirmation() {
        var actions=0
        compose.setContent { MaterialTheme { UpdateStageModal(2,"verify","لطفاً صبر کن…",{actions++},{actions++}) { Text("verifying") } } }
        Espresso.pressBack();outsideTap()
        compose.onNodeWithText("verifying").assertIsDisplayed()
        compose.onNodeWithText("لطفاً صبر کن…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0,actions) }
    }
}
