package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.example.ui.CacheTwisterScreen
import com.example.ui.CacheTwisterViewModel
import com.example.ui.theme.CacheTwisterTheme
import com.example.widget.CacheTwisterWidgetProvider

class MainActivity : ComponentActivity() {

    private val viewModel: CacheTwisterViewModel by viewModels()
    private var widgetVerifyTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY, false) == true) {
            widgetVerifyTrigger++
            intent.removeExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY)
        }

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.onAppResumed()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            CacheTwisterTheme {
                CacheTwisterScreen(
                    viewModel = viewModel,
                    launchVerifyTrigger = widgetVerifyTrigger
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY, false)) {
            widgetVerifyTrigger++
            intent.removeExtra(CacheTwisterWidgetProvider.EXTRA_FROM_WIDGET_VERIFY)
        }
    }
}
