package com.themis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.themis.mobile.ui.ThemisRoot
import com.themis.mobile.ui.ThemisTheme
import com.themis.mobile.ui.ThemisViewModel
import com.themis.mobile.ui.TrackingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: ThemisViewModel by viewModels()
    private val trackVm: TrackingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemisTheme { ThemisRoot(vm, trackVm) } }
        // While the app is visible: sync on open/resume, then every 30 s so the desktop dot stays green.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.refreshToday()
                while (true) { vm.syncNow(); delay(30_000) }
            }
        }
    }
}
