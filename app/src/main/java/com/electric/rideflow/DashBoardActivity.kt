package com.electric.rideflow

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.electric.rideflow.databinding.ActivityDashboardBinding
import com.electric.rideflow.databinding.DialogCardBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.integration.android.IntentIntegrator
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

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

    private var idCard = ""

    // Variables para SharedPreferences y Firestore
    private lateinit var sharedPref: SharedPreferences
    private lateinit var firestore: FirebaseFirestore

    // Variables para almacenar datos de usuario
    private var userId: String? = null
    private var dniIns: String? = null
    private var fullname: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Inicializar SharedPreferences
        sharedPref = getSharedPreferences("UserCache", Context.MODE_PRIVATE)

        // Recuperar datos del usuario desde SharedPreferences
        userId = sharedPref.getString("id", null)
        dniIns = sharedPref.getString("dni", null)
        fullname = sharedPref.getString("fullname", null)

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

        binding.fab.setOnClickListener {
            scanQRCode()
        }

        setupProgressDialog()
        requestLocationPermission()
    }

    private fun scanQRCode() {
        // Aquí invocarías tu escáner ZXing integrado, obtendrás el resultado en onActivityResult
        IntentIntegrator(this).initiateScan()  // Esto inicia el escaneo QR
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val documentId = result.contents
                fetchDocumentData(documentId)
            } else {
                Toast.makeText(this, "No se escaneó ningún código", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchDocumentData(documentId: String) {
        val docRef = firestore.collection("cards").document(documentId)
        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {

                val card = Card(
                    brand = documentSnapshot.getString("brand") ?: "",
                    category = documentSnapshot.getString("category") ?: "",
                    codeVest = documentSnapshot.getString("codeVest") ?: "",
                    color = documentSnapshot.getString("color") ?: "",
                    dateGenerated = documentSnapshot.getString("dateGenerated") ?: "",
                    dni = documentSnapshot.getString("dni") ?: "",
                    expiryDate = documentSnapshot.getString("expiryDate") ?: "",
                    id = documentSnapshot.getString("id") ?: "",
                    idFile = documentSnapshot.getString("idFile") ?: "",
                    idFolder = documentSnapshot.getString("idFolder") ?: "",
                    idGeneratedBy = documentSnapshot.getString("idGeneratedBy") ?: "",
                    idInCharge = documentSnapshot.getString("idInCharge") ?: "",
                    idUserAssociation = documentSnapshot.getString("idUserAssociation") ?: "",
                    inCharge = documentSnapshot.getString("inCharge") ?: "",
                    isObserved = documentSnapshot.getString("isObserved"),
                    model = documentSnapshot.getString("model") ?: "",
                    name = documentSnapshot.getString("name") ?: "",
                    nameAssociation = documentSnapshot.getString("nameAssociation") ?: "",
                    nameGeneratedBy = documentSnapshot.getString("nameGeneratedBy") ?: "",
                    nameInCharge = documentSnapshot.getString("nameInCharge") ?: "",
                    numCardOperation = documentSnapshot.getString("numCardOperation") ?: "",
                    numEngine = documentSnapshot.getString("numEngine") ?: "",
                    numResolution = documentSnapshot.getString("numResolution") ?: "",
                    numSerieVehicle = documentSnapshot.getString("numSerieVehicle") ?: "",
                    phone = documentSnapshot.getString("phone") ?: "",
                    plate = documentSnapshot.getString("plate") ?: "",
                    signatureUrl = documentSnapshot.getString("signatureUrl") ?: "",
                    status = documentSnapshot.getString("status") ?: "",
                    yearBuild = documentSnapshot.getString("yearBuild") ?: "",
                    soat = documentSnapshot.getString("soat") ?: "",
                    licence = documentSnapshot.getString("licence") ?: "",
                    dateValiditySoat = documentSnapshot.getString("dateValiditySoat") ?: "",
                    dateValidityLicence = documentSnapshot.getString("dateValidityLicence") ?: "",
                    dateValidityInspection = documentSnapshot.getString("dateValidityInspection") ?: "",
                    numPenalty = documentSnapshot.getString("numPenalty")?: "",
                    photo = documentSnapshot.getString("photo")?: "")
                showDataDialog(card)
            } else {
                Toast.makeText(this, "Documento no encontrado", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al obtener los datos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDataDialog(card: Card) {
        try {
            // Configura ViewBinding para el diseño del diálogo
            val binding = DialogCardBinding.inflate(layoutInflater)

            val status = when {
                card.status == "revok" -> "Revocado"
                isDateValid(card.expiryDate, card.status) -> "Vigente"
                else -> "Expirado"
            }
            // Aplica estilos al estado
            val styledStatus = getStyledStatusText(status)
            // Configura el texto del mensaje
            binding.tvMessage.text = SpannableStringBuilder().apply {
                append("Nombre: ${card.name}\n")
                append("DNI: ${card.dni}\n")
                append("Licencia: ${card.soat}\n")
                append("Vigencia Licencia: ${card.dateValidityLicence}\n")
                append("Fecha de Expiración: ${card.expiryDate}\n")
                append("Estado: ")
                append(styledStatus)
                append("\nSOAT: ${card.soat}\n")
                append("Vigencia SOAT: ${card.dateValiditySoat}\n")
                append("Emisión Ins.Vehicular: ${card.dateValidityInspection}\n")
                append("Nro de multas: ${card.numPenalty}")
            }

            Glide.with(this)
                .load(card.photo) // URL de la imagen
                .placeholder(R.drawable.card_photo) // Imagen de carga inicial
                .error(R.drawable.ic_warning) // Imagen si falla la carga
                .into(binding.imageTaxi) // `ImageView` objetivo

            // Construye el diálogo y lo almacena en una variable
            val dialog = AlertDialog.Builder(this)
                .setView(binding.root)
                .setCancelable(false) // Evita que se cierre al tocar fuera
                .create()

            // Configura los botones
            binding.btnPositive.setOnClickListener {
                dialog.dismiss()
            }

            binding.btnNegative.setOnClickListener {
                showReportDialog(userId, fullname, dniIns, card, latitude, longitude)
            }

            binding.btnNeutral.setOnClickListener {
                //showDetailsDialog(card)
            }

            binding.btnCustom.setOnClickListener {
                Toast.makeText(this, "Acción extra realizada", Toast.LENGTH_SHORT).show()
            }


            dialog.show()
        } catch (e: Exception) {
            // Registra el error
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun showReportDialog(userId : String?,fullname:String?,dni:String?,card: Card, lat: Double, lng: Double) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_TEXT
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Multar Usuario")
        builder.setView(editText)
        builder.setPositiveButton("Guardar") { dialog, _ ->
            val comment = editText.text.toString()
            saveReportToFirebase(userId, fullname,dni,card,comment,lat, lng)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }
        builder.create().show()
    }

    private fun saveReportToFirebase(userId : String?,fullname:String?,dni:String?,card: Card, comment : String ,lat: Double, lng: Double) {
        val db = FirebaseFirestore.getInstance()
        val incident = hashMapOf(
            "idCard" to idCard, // Suponiendo que 'idCard' sea el nombre; ajusta según necesidad
            "idUser" to userId,
            "inspector" to fullname,
            "numCardOperation" to card.numCardOperation,
            "dni" to dni,
            "lat" to lat,
            "lng" to lng,
            "comment" to comment
        )

        val cardSuspend = hashMapOf(
            "status" to "revok",
            "request" to comment
        )

        db.collection("incidents").add(incident)
        db.collection("cards").document(idCard).update(cardSuspend as Map<String, Any>)
        Toast.makeText(this,"Usuari reportado",Toast.LENGTH_SHORT).show()


    }


    private fun isDateValid(expiryDate: String?,status:String): Boolean {
        if (expiryDate.isNullOrEmpty()) return false

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        return try {
            val expiry = dateFormat.parse(expiryDate)
            val today = Calendar.getInstance().time
            !expiry.before(today)  // Si la fecha de vencimiento no es antes de hoy, está vigente
        } catch (e: ParseException) {
            false  // Si el formato de la fecha no es válido
        }
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

    private fun removeGoogleAuthProvider() {
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // Re-autenticar al usuario si es necesario (recomendado)
            val email = user.email
            val password = "megamanxxx678" // Debes obtener esta contraseña de manera segura

            if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        // Desvincular el proveedor de Google
                        user.unlink("google.com").addOnCompleteListener { unlinkTask ->
                            if (unlinkTask.isSuccessful) {
                                Toast.makeText(this, "Método de autenticación de Google eliminado", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Error al desvincular Google: ${unlinkTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error en la reautenticación: ${reauthTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Correo o contraseña no disponibles", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getStyledStatusText(status: String): SpannableString {
        val spannable = SpannableString(status)

        when (status) {
            "Revocado", "Expirado" -> {
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED), // Color rojo
                    0, status.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD), // Negrita
                    0, status.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            "Vigente" -> {
                spannable.setSpan(
                    ForegroundColorSpan(Color.GREEN), // Color verde
                    0, status.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD), // Negrita
                    0, status.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

}
