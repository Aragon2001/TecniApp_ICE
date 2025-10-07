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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriasRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de inicio de sesión orientada a “Room-first”.
 *
 * Decisiones clave:
 * - La verificación de existencia de cuenta se hace con FirebaseAuth (fetchSignInMethodsForEmail)
 *   para evitar reglas de lectura amplias en RTDB.
 * - El perfil del usuario en RTDB se maneja por UID (nodo /usuarios/{uid}).
 * - La app guarda sesión en dos SharedPreferences por compatibilidad con código previo
 *   y con el módulo de sincronización.
 * - Tras iniciar sesión, se sincroniza la subregión del usuario y luego se navega a la pantalla principal.
 */
class LoginActivity : AppCompatActivity() {

    // --- UI ---
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var showPasswordIcon: ImageView
    private lateinit var signInButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var forgotPasswordButton: View

    // --- Estado / servicios ---
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var database: DatabaseReference
    private lateinit var roomRepository: RoomRepository
    private lateinit var averiasRepository: AveriasRepository
    private lateinit var synchronizer: Synchronizer

    private companion object {
        // Instancia RTDB que contiene los perfiles de usuario (colección /usuarios)
        const val DATABASE_URL_USERS = "https://tecniapp-ice-user.firebaseio.com"

        // Preferencias históricas (compatibilidad)
        const val LEGACY_PREFS = "TecniAppPrefs"
        const val LEGACY_KEY_LOGGED_IN = "isLoggedIn"

        // Preferencias usadas por el módulo de sincronización
        const val SYNC_PREFS = "app_preferences"
        const val SYNC_KEY_LOGGED_IN = "is_logged_in"

        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de servicios base
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance(DATABASE_URL_USERS).reference
        val localDb = AppDatabase.getInstance(applicationContext)
        roomRepository = RoomRepository.getInstance(applicationContext)
        averiasRepository = AveriasRepository(localDb)
        synchronizer = Synchronizer(roomRepository, averiasRepository)

        // Si ya existe sesión (por cualquiera de los dos flags), navegar directo a Main
        sharedPreferences = getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE)
        val isLoggedInLegacy = sharedPreferences.getBoolean(LEGACY_KEY_LOGGED_IN, false)
        val isLoggedInSync = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE)
            .getBoolean(SYNC_KEY_LOGGED_IN, false)
        if (isLoggedInLegacy || isLoggedInSync) {
            startActivity(Intent(this, ActivityMain::class.java))
            finish()
            return
        }

        // Inflado de layout y wiring de vista
        setContentView(R.layout.login)
        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        showPasswordIcon = findViewById(R.id.password_icon)
        signInButton = findViewById(R.id.sign_in_button)
        registerButton = findViewById(R.id.register_button)
        forgotPasswordButton = findViewById(R.id.forgot_password_button)

        // Acciones de UI
        signInButton.setOnClickListener { signIn() }
        registerButton.setOnClickListener { startActivity(Intent(this, RegistroActivity::class.java)) }
        forgotPasswordButton.setOnClickListener { forgotPassword() }

        // Toggle de visibilidad de contraseña conservando posición del cursor
        showPasswordIcon.setOnClickListener {
            val isVisible = passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordEditText.inputType =
                if (isVisible) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordEditText.setSelection(passwordEditText.text?.length ?: 0)
            showPasswordIcon.setImageResource(
                if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            )
        }
    }

    /**
     * Crea el nodo mínimo de usuario en RTDB bajo /usuarios/{uid} si no existe.
     * Se persiste también "email_lower" para futuras búsquedas internas case-insensitive
     * (no se usa en login; la verificación de cuenta se hace con FirebaseAuth).
     */
    private fun createUserRtdbIfMissing(uid: String, email: String) {
        val userRef = FirebaseDatabase.getInstance(DATABASE_URL_USERS)
            .reference.child("usuarios").child(uid)

        userRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val emailLower = email.trim().lowercase()
                    val userMap = mapOf(
                        "uid" to uid,
                        "email" to email,
                        "email_lower" to emailLower,
                        "nombre" to "",
                        "apellidos" to "",
                        "primer_apellido" to "",
                        "segundo_apellido" to "",
                        "cedula" to "",
                        "region" to "",
                        "region_nombre" to "",
                        "subregion" to "",
                        "subregion_nombre" to "",
                        "agencia" to "",
                        "agencia_id" to "",
                        "placaVehiculo" to "",
                        "telefono" to "",
                        "password" to ""
                    )
                    userRef.setValue(userMap)
                }
            }
            .addOnFailureListener {
                Log.w(TAG, "No se pudo verificar/crear nodo de usuario en RTDB: ${it.message}", it)
            }
    }

    /**
     * Inicio de sesión con FirebaseAuth:
     * - Valida email/contraseña localmente.
     * - Realiza sign-in.
     * - Marca sesión en SharedPreferences (compatibilidad + sync).
     * - Asegura que exista un perfil en RTDB bajo /usuarios/{uid}.
     * - Upsert del usuario a Room desde RTDB (por UID).
     * - Sincroniza la subregión asociada y navega a Main al finalizar.
     */
    private fun signIn() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "El correo es requerido"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Formato de correo inválido"
            return
        }
        if (password.isEmpty()) {
            passwordEditText.error = "La contraseña es requerida"
            return
        }

        signInButton.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                signInButton.isEnabled = true
                if (!task.isSuccessful) {
                    handleAuthError(task.exception)
                    return@addOnCompleteListener
                }

                // Persistencia de estado de sesión (dos ubicaciones por compatibilidad)
                markLoggedIn()

                val uid = auth.currentUser?.uid ?: run {
                    Toast.makeText(this, "No se pudo obtener el UID del usuario.", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                // Diálogo de progreso de sincronización
                val dlg = SyncDialogFragment.show(supportFragmentManager).apply {
                    setHeader("Sincronizando…")
                    update(0, 0, "Preparando…")
                }

                lifecycleScope.launch {
                    try {
                        // 1) Perfil en Room desde RTDB. Si no existe en RTDB, se crea mínimo y se reintenta.
                        val user = withContext(Dispatchers.IO) {
                            try {
                                roomRepository.upsertUserFromFirebase(uid)
                            } catch (_: Exception) {
                                createUserRtdbIfMissing(uid, email)
                                roomRepository.upsertUserFromFirebase(uid)
                            }
                        }

                        // 2) Sincronización de subregión (los datos operativos dependen de esto)
                        val subId = user.subregion
                        synchronizer.syncSubregion(
                            subId.toString(),
                            onSyncStart = { message ->
                                if (!isFinishing && !isDestroyed) {
                                    runOnUiThread { dlg.setHeader(message) }
                                }
                            },
                            onSyncProgress = { done, total, msg ->
                                if (!isFinishing && !isDestroyed) {
                                    runOnUiThread { dlg.update(done, total, msg ?: "") }
                                }
                            },
                            onSyncSuccess = {
                                if (!isFinishing && !isDestroyed) {
                                    dlg.dismissAllowingStateLoss()
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Inicio de sesión exitoso",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    startActivity(Intent(this@LoginActivity, ActivityMain::class.java))
                                    finish()
                                }
                            },
                            onSyncError = { err ->
                                if (!isFinishing && !isDestroyed) {
                                    dlg.dismissWithError(err.message ?: "Error de sincronización") { signIn() }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        if (!isFinishing && !isDestroyed) {
                            dlg.dismissWithError(e.localizedMessage ?: "Error inesperado") { signIn() }
                        }
                        Log.e(TAG, "Error en sincronización inicial: ${e.message}", e)
                    }
                }
            }
            .addOnFailureListener { ex ->
                signInButton.isEnabled = true
                Toast.makeText(this, "Error de autenticación: ${ex.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Auth failure: ${ex.message}", ex)
            }
    }

    /**
     * Marcado de sesión en dos preferencias:
     * - LEGACY_PREFS: usado por código histórico que espera esta bandera.
     * - SYNC_PREFS: usado por el módulo de sincronización.
     */
    private fun markLoggedIn() {
        getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE).edit()
            .putBoolean(LEGACY_KEY_LOGGED_IN, true)
            .apply()

        getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).edit()
            .putBoolean(SYNC_KEY_LOGGED_IN, true)
            .apply()
    }

    /**
     * Lectura auxiliar del perfil por UID (solo para logging o precarga).
     * No es necesaria para el login en sí, ya que la sincronización persiste en Room.
     */
    private fun loadUserDataByUid(uid: String) {
        FirebaseDatabase.getInstance(DATABASE_URL_USERS)
            .reference.child("usuarios").child(uid)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    Log.w(TAG, "Perfil no encontrado en RTDB para uid=$uid")
                    return@addOnSuccessListener
                }
                val nombre = snap.child("nombre").getValue(String::class.java).orEmpty()
                val apellido1 = snap.child("primer_apellido").getValue(String::class.java).orEmpty()
                val apellido2 = snap.child("segundo_apellido").getValue(String::class.java).orEmpty()
                val apellidos = listOf(apellido1, apellido2)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { snap.child("apellidos").getValue(String::class.java).orEmpty() }
                val placa = snap.child("placaVehiculo").getValue(String::class.java).orEmpty()
                val subR = snap.child("subregion").getValue(String::class.java).orEmpty()
                val region = snap.child("region").getValue(String::class.java).orEmpty()
                val agencia = snap.child("agencia").getValue(String::class.java).orEmpty()
                Log.d(
                    TAG,
                    "Perfil RTDB -> $nombre $apellidos, region=$region, agencia=$agencia, placa=$placa, subregion=$subR"
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error leyendo perfil por UID: ${e.message}", e)
            }
    }

    /**
     * Mapeo de errores comunes de FirebaseAuth a mensajes comprensibles.
     */
    private fun handleAuthError(ex: Exception?) {
        when (ex) {
            is FirebaseAuthInvalidUserException ->
                Toast.makeText(this, "Usuario no existe o fue deshabilitado.", Toast.LENGTH_LONG).show()
            is FirebaseAuthInvalidCredentialsException ->
                Toast.makeText(this, "Credenciales inválidas. Verifica tu contraseña.", Toast.LENGTH_LONG).show()
            else ->
                Toast.makeText(this, "No se pudo iniciar sesión: ${ex?.message ?: "Error desconocido"}", Toast.LENGTH_LONG).show()
        }
        Log.e(TAG, "Auth error: ${ex?.message}", ex)
    }

    /**
     * Recuperación de contraseña vía FirebaseAuth.
     * Envía un correo al email indicado si existe una cuenta asociada.
     */
    private fun forgotPassword() {
        val email = emailEditText.text.toString().trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo válido para recuperar contraseña.", Toast.LENGTH_SHORT).show()
            return
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Te enviamos un correo para recuperar tu contraseña.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "No pudimos enviar el correo: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Password reset error: ${e.message}", e)
            }
    }
}
