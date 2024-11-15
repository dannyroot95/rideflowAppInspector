package com.electric.rideflow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.electric.rideflow.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val RC_SIGN_IN = 100
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        // Configuración de Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        binding.txtGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.loginButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        startLogin()
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)!!
                firebaseAuthWithGoogle(account)
            } catch (e: Exception) {
                stopLogin()
                Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.let {
                    checkAndSaveUserData(it.uid, it.email ?: "", it.displayName ?: "")
                }
            } else {
                stopLogin()
                Toast.makeText(this, "Error en la autenticación con Google", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndSaveUserData(userId: String, email: String, name: String) {
        val userRef = firestore.collection("users").document(userId)
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val dni = document.getString("dni")
                val phone = document.getString("phone")
                val lastName = document.getString("lastName") ?: ""

                if (dni.isNullOrEmpty() || phone.isNullOrEmpty()) {
                    // Mostrar modal para completar los datos
                    showDataCompletionModal(userId, name)
                } else {
                    // Guardar en caché y redirigir al Dashboard
                    saveUserToCache(userId, "$name $lastName",dni)
                    navigateToDashboard()
                }
            } else {
                // Crear nuevo usuario en Firestore
                userRef.set(
                    mapOf(
                        "id" to userId,
                        "email" to email,
                        "name" to name,
                        "lastName" to "",
                        "dni" to "",
                        "phone" to "",
                        "status" to "on",
                        "typeUser" to "client"
                    )
                ).addOnSuccessListener {
                    // Mostrar modal para completar los datos
                    showDataCompletionModal(userId, name)
                }
            }
        }
    }

    private fun showDataCompletionModal(userId: String, name: String) {
        stopLogin()
        val dialog = DataCompletionDialog(this)
        dialog.setOnSaveListener { dni, phone ->
            val userRef = firestore.collection("users").document(userId)
            userRef.update("dni", dni, "phone", phone)
            saveUserToCache(userId, "$name",dni)
            Toast.makeText(this, "Datos completados correctamente", Toast.LENGTH_SHORT).show()
            navigateToDashboard()
        }
        dialog.show()
    }

    private fun saveUserToCache(userId: String, fullName: String, dni: String) {
        val sharedPref = getSharedPreferences("UserCache", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("id", userId)
        editor.putString("fullname", fullName)
        editor.putString("dni", dni)
        editor.apply()
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashBoardActivity::class.java))
        finish()
    }

    private fun startLogin(){
        binding.layoutLogin.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun stopLogin(){
        binding.layoutLogin.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        val sharedPref = getSharedPreferences("UserCache", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("id", null)
        val dni = sharedPref.getString("dni", null)
        if (userId != null) {
            if (dni != null) {
                navigateToDashboard()
            }
        }
    }

}
