package com.intercom.ride.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intercom.ride.core.ConnectionState
import com.intercom.ride.core.IntercomError

class MainActivity : ComponentActivity() {
    private val vm: IntercomViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntercomTheme {
                Box(Modifier.fillMaxSize().background(IcColors.Bg).safeDrawingPadding()) {
                    AppRoot(vm)
                }
            }
        }
    }
}

/** Actions the UI needs from the Activity (system dialogs and settings pages). */
class SystemActions(
    val requestPermissions: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
    val openWifiSettings: () -> Unit,
    val openAppSettings: () -> Unit,
    val requestBatteryExemption: () -> Unit,
)

@Composable
fun AppRoot(vm: IntercomViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val perms = vm.session.permissions

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (screen == Screen.NEARBY) vm.refresh()
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refresh()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (screen == Screen.NEARBY) vm.refresh()
    }
    val actions = SystemActions(
        requestPermissions = { permLauncher.launch(perms.required.toTypedArray()) },
        enableBluetooth = {
            val missing = perms.missingCritical()
            if (missing.isNotEmpty()) permLauncher.launch(perms.required.toTypedArray())
            else runCatching { btLauncher.launch(bluetoothEnableIntent()) }
        },
        openLocationSettings = { settingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        openWifiSettings = { settingsLauncher.launch(Intent(Settings.Panel.ACTION_WIFI)) },
        openAppSettings = { ctx.startActivity(perms.appSettingsIntent()) },
        requestBatteryExemption = { runCatching { ctx.startActivity(perms.batteryOptimizationIntent()) } },
    )

    // Ask for everything once when reaching the device list.
    LaunchedEffect(screen) {
        if (screen == Screen.NEARBY) {
            if (perms.missing().isNotEmpty()) actions.requestPermissions() else vm.startDiscovery()
        }
    }

    // After a session ends, go straight back to searching.
    LaunchedEffect(state is ConnectionState.Idle, screen) {
        if (screen == Screen.NEARBY && state is ConnectionState.Idle && perms.missingCritical().isEmpty()) {
            vm.startDiscovery()
        }
    }

    BackHandler(enabled = screen != Screen.WELCOME && !state.isSessionActive) { vm.back() }

    when {
        screen == Screen.SETTINGS -> SettingsScreen(vm, actions)
        state.isSessionActive -> ActiveIntercomScreen(vm)
        screen == Screen.WELCOME -> WelcomeScreen(onStart = { vm.go(Screen.ROLE) })
        screen == Screen.ROLE -> RoleScreen(vm)
        else -> NearbyScreen(vm, actions)
    }

    val s = state
    if (s is ConnectionState.AwaitingDecision) {
        ConnectionRequestDialog(s.request, onAccept = vm::accept, onReject = vm::reject)
    }
}

/** Only called after PermissionManager confirmed BLUETOOTH_CONNECT is granted. */
@SuppressLint("MissingPermission")
private fun bluetoothEnableIntent() = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

fun errorAction(e: IntercomError, a: SystemActions): Pair<String, () -> Unit>? = when (e) {
    is IntercomError.PermissionDenied -> "Grant permissions" to a.requestPermissions
    IntercomError.BluetoothOff -> "Turn on Bluetooth" to a.enableBluetooth
    IntercomError.LocationOff -> "Open Location settings" to a.openLocationSettings
    IntercomError.WifiOff -> "Turn on Wi-Fi" to a.openWifiSettings
    else -> null
}
