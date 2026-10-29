package app.pulse.spike.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** True once the user has allowed everything Bluetooth scanning and connecting needs. */
fun hasBlePermissions(context: Context): Boolean {
    val needed = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
