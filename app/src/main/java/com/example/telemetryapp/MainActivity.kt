package com.example.telemetryapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.telemetryapp.ui.TelemetryApp

class MainActivity : ComponentActivity() {
    private val viewModel: TelemetryViewModel by viewModels {
        TelemetryViewModelFactory(application)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionResult(results)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    TelemetryApp(viewModel = viewModel)
                    LaunchedEffect(Unit) {
                        val permissions = arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                        permissionLauncher.launch(permissions)
                    }
                }
            }
        }
    }
}
