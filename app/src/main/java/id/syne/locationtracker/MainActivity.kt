package id.syne.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.syne.locationtracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                checkBackgroundLocationPermission()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                checkBackgroundLocationPermission()
            }
            else -> {
                showPermissionDeniedDialog()
            }
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for location tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        updateUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    private fun setupClickListeners() {
        binding.startButton.setOnClickListener {
            when {
                !isGpsEnabled() -> {
                    showGpsDisabledDialog()
                }
                hasLocationPermissions() -> {
                    startLocationTracking()
                }
                else -> {
                    requestLocationPermissions()
                }
            }
        }

        binding.stopButton.setOnClickListener {
            stopLocationTracking()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return (fineLocation || coarseLocation) && backgroundLocation
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundLocationDialog()
            } else {
                if (isGpsEnabled()) {
                    startLocationTracking()
                } else {
                    showGpsDisabledDialog()
                }
            }
        } else {
            if (isGpsEnabled()) {
                startLocationTracking()
            } else {
                showGpsDisabledDialog()
            }
        }
    }

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Required")
            .setMessage("This app needs background location access to track your location when the app is closed. Please select 'Allow all the time' in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_REQUEST_CODE
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("Location permission is required for this app to work. Please grant location permission in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == BACKGROUND_LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Check GPS after permission is granted
                if (isGpsEnabled()) {
                    startLocationTracking()
                } else {
                    showGpsDisabledDialog()
                }
            } else {
                Toast.makeText(this, "Background location permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showGpsDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS Required")
            .setMessage("GPS/Location Services must be enabled for location tracking to work. Please enable GPS in your device settings.")
            .setPositiveButton("Enable GPS") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "GPS is required for location tracking", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLocationTracking() {
        if (!isGpsEnabled()) {
            showGpsDisabledDialog()
            return
        }

        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val prefs = getSharedPreferences("location_settings", MODE_PRIVATE)
        prefs.edit().putBoolean("tracking_enabled", true).apply()

        updateUI()
        Toast.makeText(this, "Location tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationTracking() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        stopService(serviceIntent)

        val prefs = getSharedPreferences("location_settings", MODE_PRIVATE)
        prefs.edit().putBoolean("tracking_enabled", false).apply()

        updateUI()
        Toast.makeText(this, "Location tracking stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val isServiceRunning = LocationTrackingService.isServiceRunning
        val isGpsEnabled = isGpsEnabled()

        when {
            isServiceRunning -> {
                binding.statusText.text = "Location tracking is ACTIVE"
                binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.startButton.isEnabled = false
                binding.stopButton.isEnabled = true
            }
            !isGpsEnabled -> {
                binding.statusText.text = "GPS is DISABLED - Enable GPS to start tracking"
                binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.startButton.isEnabled = true
                binding.startButton.text = "ENABLE GPS & START"
                binding.stopButton.isEnabled = false
            }
            else -> {
                binding.statusText.text = "Location tracking is STOPPED"
                binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.startButton.isEnabled = true
                binding.startButton.text = "START TRACKING"
                binding.stopButton.isEnabled = false
            }
        }
    }

    companion object {
        private const val BACKGROUND_LOCATION_REQUEST_CODE = 1001
    }
}