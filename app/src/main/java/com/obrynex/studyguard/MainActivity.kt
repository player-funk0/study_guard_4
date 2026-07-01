package com.obrynex.studyguard

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.obrynex.studyguard.navigation.NavGraph
import com.obrynex.studyguard.ui.theme.StudyGuardTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow content to render into display cut-outs (notches, punch-holes).
        // Without this, devices with a cut-out show a black letterbox bar at the
        // top even with edge-to-edge enabled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            StudyGuardTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                NavGraph(windowSizeClass = windowSizeClass)
            }
        }
    }
}
