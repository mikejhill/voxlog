package com.mikejhill.voxlog.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mikejhill.voxlog.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the store icon (512×512) and feature graphic (1024×500) from the app's own vector assets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreGraphicsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun icon() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(BRAND), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(512.dp))
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/store_icon.png")
    }

    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun featureGraphic() {
        composeRule.setContent {
            Row(
                Modifier.fillMaxSize().background(BRAND).padding(horizontal = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(56.dp)).background(Color(0xFF293CA0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(240.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("VoxLog", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold)
                    Text("Talk. Tap stop. Done.", color = Color(0xFFFFDADA), fontSize = 40.sp)
                    Text("Private, on-device voice notes", color = Color(0xFFDEE0FF), fontSize = 28.sp)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/store_feature_graphic.png")
    }

    private companion object {
        val BRAND = Color(0xFF4355B9)
    }
}
