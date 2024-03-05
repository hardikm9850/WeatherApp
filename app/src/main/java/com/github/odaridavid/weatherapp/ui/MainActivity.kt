package com.github.odaridavid.weatherapp.ui

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.github.odaridavid.weatherapp.MainViewIntent
import com.github.odaridavid.weatherapp.MainViewModel
import com.github.odaridavid.weatherapp.MainViewState
import com.github.odaridavid.weatherapp.common.CheckForPermissions
import com.github.odaridavid.weatherapp.common.OnPermissionDenied
import com.github.odaridavid.weatherapp.common.createLocationRequest
import com.github.odaridavid.weatherapp.designsystem.EnableLocationSettingScreen
import com.github.odaridavid.weatherapp.designsystem.LoadingScreen
import com.github.odaridavid.weatherapp.designsystem.RequiresPermissionsScreen
import com.github.odaridavid.weatherapp.designsystem.theme.WeatherAppTheme
import com.github.odaridavid.weatherapp.ui.update.UpdateManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var updateManager: UpdateManager

    private val locationRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mainViewModel.processIntent(MainViewIntent.CheckLocationSettings(isEnabled = true))
            } else {
                mainViewModel.processIntent(MainViewIntent.CheckLocationSettings(isEnabled = false))
            }
        }
    private val permissionRequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            mainViewModel.processIntent(MainViewIntent.GrantPermission(isGranted = isGranted))
        }

    private val updateRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // TODO Trigger a UI event
                Log.d("MainActivity", "Update successful")
            } else {
                Log.e("MainActivity", "Update failed")
            }
        }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateManager.checkForUpdates(activityResultLauncher = updateRequestLauncher)

        createLocationRequest(
            activity = this@MainActivity,
            locationRequestLauncher = locationRequestLauncher
        ) {
            mainViewModel.processIntent(MainViewIntent.CheckLocationSettings(isEnabled = true))
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {

            WeatherAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val state = mainViewModel.state.collectAsState().value

                    CheckForPermissions(
                        onPermissionGranted = {
                            mainViewModel.processIntent(MainViewIntent.GrantPermission(isGranted = true))
                        },
                        onPermissionDenied = {
                            OnPermissionDenied(activityPermissionResult = permissionRequestLauncher)
                        }
                    )

                    InitMainScreen(state)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun InitMainScreen(state: MainViewState) {
        when {
            state.isLocationSettingEnabled && state.isPermissionGranted -> {
                fusedLocationProviderClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        location?.run {
                            mainViewModel.processIntent(
                                MainViewIntent.ReceiveLocation(
                                    longitude = location.longitude,
                                    latitude = location.latitude
                                )
                            )
                        }
                    }.addOnFailureListener { exception ->
                        mainViewModel.processIntent(MainViewIntent.LogException(throwable = exception))
                    }
                WeatherAppScreensConfig(navController = rememberNavController())
            }

            state.isLocationSettingEnabled && !state.isPermissionGranted -> {
                RequiresPermissionsScreen()
            }

            !state.isLocationSettingEnabled && !state.isPermissionGranted -> {
                EnableLocationSettingScreen()
            }

            else -> LoadingScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.unregisterListeners()
    }
}

