package com.electric.rideflow

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.electric.rideflow.databinding.ScanActivityBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.integration.android.IntentIntegrator
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ScanActivityBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ScanActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        firestore = FirebaseFirestore.getInstance()

        // Configura el botón para escanear el código QR
        binding.scanButton.setOnClickListener {
            scanQRCode()
        }
    }

    private fun scanQRCode() {
        // Aquí invocarías tu escáner ZXing integrado, obtendrás el resultado en onActivityResult
        IntentIntegrator(this).initiateScan()  // Esto inicia el escaneo QR
    }

    // Método para manejar el resultado del escaneo QR
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

    // Buscar documento en Firestore
    private fun fetchDocumentData(documentId: String) {
        val docRef = firestore.collection("cards").document(documentId)
        docRef.get().addOnSuccessListener { documentSnapshot ->
            if (documentSnapshot.exists()) {
                val name = documentSnapshot.getString("name")
                val dni = documentSnapshot.getString("dni")
                val expiryDate = documentSnapshot.getString("expiryDate")

                showDataDialog(name, dni,expiryDate)
            } else {
                Toast.makeText(this, "Documento no encontrado", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al obtener los datos", Toast.LENGTH_SHORT).show()
        }
    }

    // Mostrar los datos en un Dialog, incluyendo la vigencia de expiryDate
    private fun showDataDialog(name: String?, dni: String?, expiryDate: String?) {
        val builder = AlertDialog.Builder(this)

        // Verifica si expiryDate está vigente o no
        val isVigente = isDateValid(expiryDate)

        val status = if (isVigente) "Vigente" else "Expirado"

        builder.setTitle("Datos de la tarjeta")
        builder.setMessage("Nombre: $name\nDNI: $dni\nFecha de Expiración: $expiryDate\nEstado: $status")
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    // Función para validar si la fecha es vigente o no
    private fun isDateValid(expiryDate: String?): Boolean {
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
}