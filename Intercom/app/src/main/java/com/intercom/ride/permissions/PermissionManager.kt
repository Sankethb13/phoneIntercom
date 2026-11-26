package com.intercom.ride.permissions

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.intercom.ride.core.IntercomError

/** Runtime permission and radio-state checks. */
class PermissionManager(private val context: Context) {
    val required: List<String> get() = PermissionRequirements.required(Build.VERSION.SDK_INT)

    fun missing(): List<String> = required.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun missingCritical(): List<String> = missing().filter(PermissionRequirements::isCritical)

    fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(context, PermissionRequirements.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isBluetoothOn(): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    fun isWifiOn(): Boolean =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

    fun isLocationOn(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isLocationEnabled
    }

    fun isIgnoringBatteryOptimizations(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    fun hasPlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** The first blocking problem, or null when discovery can start. */
    fun readinessError(): IntercomError? {
        if (!hasPlayServices()) return IntercomError.PlayServicesMissing
        val miss = missingCritical()
        if (miss.isNotEmpty()) {
            return IntercomError.PermissionDenied(miss.map(PermissionRequirements::friendlyName).distinct())
        }
        if (!isBluetoothOn()) return IntercomError.BluetoothOff
        if (PermissionRequirements.needsLocationServices(Build.VERSION.SDK_INT) && !isLocationOn()) {
            return IntercomError.LocationOff
        }
        return null
    }

    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
