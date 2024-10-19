package com.Arasoftsolutions.tecniapp_ice

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.Arasoftsolutions.tecniapp_ice.User.UserViewModel
import com.Arasoftsolutions.tecniapp_ice.User.User
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference // Realtime Database
    private lateinit var showPasswordIcon: ImageView
    private val userViewModel: UserViewModel by viewModels()

    private companion object {
        const val DATABASE_URL = "https://tecniapp-ice-user.firebaseio.com"
        const val PREFS_NAME = "TecniAppPrefs"
        const val KEY_IS_LOGGED_IN = "isLoggedIn"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar FirebaseAuth y Realtime Database
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance(DATABASE_URL).reference // URL específica de la base de datos

        // Verifica si el usuario ya está logueado
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)

        if (isLoggedIn) {
            // Navegar directamente a la actividad principal si ya está logueado
            val intent = Intent(this, ActivityMain::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.login)

        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        showPasswordIcon = findViewById(R.id.password_icon)

        // Botón de "Iniciar sesión"
        val signInButton: MaterialButton = findViewById(R.id.sign_in_button)
        signInButton.setOnClickListener { signIn() }

        // Botón de "Registrarme"
        val registerButton: MaterialButton = findViewById(R.id.register_button)
        registerButton.setOnClickListener {
            // Navegar a RegistroActivity
            val intent = Intent(this, RegistroActivity::class.java)
            startActivity(intent)
        }

        // Botón de "Olvidé mi contraseña"
        val forgotPasswordButton: View = findViewById(R.id.forgot_password_button)
        forgotPasswordButton.setOnClickListener { forgotPassword() }

        // Lógica para mostrar/ocultar la contraseña
        showPasswordIcon.setOnClickListener {
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                showPasswordIcon.setImageResource(R.drawable.ic_visibility) // Cambia al ícono de mostrar
            } else {
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                showPasswordIcon.setImageResource(R.drawable.ic_visibility_off) // Cambia al ícono de ocultar
            }
            passwordEditText.setSelection(passwordEditText.text.length) // Mantener el cursor al final
        }
    }

    private fun signIn() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "El correo es requerido"
            return
        }
        if (password.isEmpty()) {
            passwordEditText.error = "La contraseña es requerida"
            return
        }

        // Validar formato de correo electrónico
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Formato de correo inválido"
            return
        }

        // Autenticarse con Firebase Authentication
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Inicio de sesión exitoso
                    Toast.makeText(this@LoginActivity, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

                    // Obtener el usuario actualmente autenticado
                    val currentUser = auth.currentUser
                    currentUser?.let { user ->
                        // Cargar datos adicionales del usuario desde Realtime Database
                        loadUserData(user.email ?: "")
                    }
                } else {
                    handleAuthError(task.exception)
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(this@LoginActivity, "Error de autenticación: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("LoginActivity", "Error de autenticación: ${exception.message}")
            }
    }

    private fun loadUserData(email: String) {
        database.child("usuarios").orderByChild("email").equalTo(email).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (userSnapshot in dataSnapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)

                        user?.let { userObj ->
                            // Almacenar el usuario en el UserViewModel
                            userViewModel.updateUserData(userObj)

                            // Almacenar en SharedPreferences
                            val editor = sharedPreferences.edit()
                            editor.putBoolean(KEY_IS_LOGGED_IN, true)
                            editor.apply()

                            // Navegar a la actividad principal
                            startActivity(Intent(this@LoginActivity, ActivityMain::class.java))
                            finish() // Cerrar la actividad de inicio de sesión
                        }
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@LoginActivity, "Error al obtener datos del usuario: ${databaseError.message}", Toast.LENGTH_SHORT).show()
                Log.e("LoginActivity", "Error al obtener datos del usuario: ${databaseError.message}")
            }
        })
    }

    private fun handleAuthError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidCredentialsException -> {
                passwordEditText.error = "Contraseña incorrecta"
            }
            is FirebaseAuthInvalidUserException -> {
                emailEditText.error = "El usuario no existe"
            }
            else -> {
                Toast.makeText(this@LoginActivity, "Error: ${exception?.message}", Toast.LENGTH_SHORT).show()
                Log.e("LoginActivity", "Error: ${exception?.message}")
            }
        }
    }

    private fun forgotPassword() {
        val email = emailEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "El correo es requerido"
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this@LoginActivity, "Correo de recuperación enviado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Error al enviar el correo de recuperación", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
