package com.electric.rideflow

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.electric.rideflow.databinding.ActivityDashboardBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class DashBoardActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var progressDialog: Dialog
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationCallback: LocationCallback
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var isRedirectingToSettings = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar Google Maps
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializar FusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar el ActivityResultLauncher para permisos
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                checkGPSAndFetchLocation()
            } else {
                handlePermissionDenied()
            }
        }

        // Configurar el LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult.locations.isEmpty()) {
                    Toast.makeText(this@DashBoardActivity, "No se pudo obtener la ubicación. Reintentando...", Toast.LENGTH_SHORT).show()
                    return
                }

                locationResult.lastLocation?.let { location ->
                    latitude = location.latitude
                    longitude = location.longitude

                    if (latitude != 0.0 && longitude != 0.0) {
                        progressDialog.dismiss() // Cierra el diálogo
                        updateMapLocation(LatLng(latitude, longitude)) // Actualiza la ubicación en el mapa
                    }
                }
            }
        }

        setupProgressDialog()
        requestLocationPermission()
    }

    private fun isGPSEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    }


    private fun setupProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog.setContentView(R.layout.dialog_progress)
        progressDialog.setCancelable(false) // Evita que el usuario lo cierre manualmente
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            showPermissionRationaleDialog()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage("Esta aplicación necesita acceso a tu ubicación para funcionar correctamente.")
            .setPositiveButton("Aceptar") { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                handlePermissionDenied()
            }
            .create()
            .show()
    }

    private fun handlePermissionDenied() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage("El permiso de ubicación ha sido denegado permanentemente. Debes habilitarlo manualmente desde la configuración de la aplicación.")
                .setPositiveButton("Ir a configuración") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "No puedes usar esta funcionalidad sin permisos", Toast.LENGTH_SHORT).show()
                }
                .create()
                .show()
        } else {
            Toast.makeText(this, "Permiso de ubicación es obligatorio", Toast.LENGTH_SHORT).show()
            requestLocationPermission()
        }
    }

    private fun checkGPSAndFetchLocation() {
        if (isRedirectingToSettings) {
            return // Evita múltiples redirecciones
        }

        if (isGPSEnabled()) {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 5000
                fastestInterval = 2000
            }

            startLocationUpdates(locationRequest) // Inicia las actualizaciones si el GPS está habilitado
        } else {
            isRedirectingToSettings = true // Establece el flag para evitar redirecciones adicionales
            Toast.makeText(this, "GPS es obligatorio. Por favor, actívalo.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }


    private fun startLocationUpdates(locationRequest: LocationRequest) {
        if (!isGPSEnabled()) {
            Toast.makeText(this, "GPS es obligatorio. Por favor, actívalo.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        progressDialog.show()
    }


    private fun vectorToBitmap(resourceId: Int, tintColor: Int): Bitmap? {
        val vectorDrawable = resources.getDrawable(resourceId, null)
        if (vectorDrawable == null) {
            Toast.makeText(this, "Error al cargar el vector.", Toast.LENGTH_SHORT).show()
            return null
        }
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.setTint(tintColor) // Aplicar un tinte si es necesario
        vectorDrawable.draw(canvas)
        return bitmap
    }

    private fun updateMapLocation(latLng: LatLng) {
        googleMap.clear()

        val bitmap = vectorToBitmap(R.drawable.ic_inspector, resources.getColor(android.R.color.black, null))
        if (bitmap == null) {
            Toast.makeText(this, "Error al procesar el ícono del marcador.", Toast.LENGTH_SHORT).show()
            return
        }

        val markerOptions = MarkerOptions()
            .position(latLng)
            .title("Inspector")
            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))

        val marker = googleMap.addMarker(markerOptions)
        marker?.showInfoWindow()

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        val defaultLocation = LatLng(-12.594759670987276, -69.19915586510614)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))

        requestLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        isRedirectingToSettings = false // Restaura el flag al regresar al Activity
        if (isGPSEnabled()) {
            checkGPSAndFetchLocation()
        }
    }


    private fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }
}
