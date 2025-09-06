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
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.Database.sync.Synchronizer
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.User.UserViewModel
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de inicio de sesión (Room-first).
 *
 * Flujo:
 * 1) Sign-in con Firebase Auth.
 * 2) Guardar flags de sesión en SharedPreferences (legacy + sync).
 * 3) Guardar usuario en Room (upsert desde Firebase por UID).
 * 4) Sincronizar subregión del usuario mostrando un modal de progreso.
 * 5) Navegar a ActivityMain (UI ya leerá desde Room).
 */
class LoginActivity : AppCompatActivity() {

    // UI
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var showPasswordIcon: ImageView
    private lateinit var signInButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var forgotPasswordButton: View

    // Estado / servicios
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var database: DatabaseReference
    private lateinit var roomRepository: RoomRepository
    private lateinit var synchronizer: Synchronizer

    // VM (si luego quieres observar info de usuario/logs)
    private val userViewModel: UserViewModel by viewModels()

    private companion object {
        const val DATABASE_URL_USERS = "https://tecniapp-ice-user.firebaseio.com" // RTDB de usuarios
        const val LEGACY_PREFS = "TecniAppPrefs"
        const val LEGACY_KEY_LOGGED_IN = "isLoggedIn"
        const val SYNC_PREFS = "app_preferences"
        const val SYNC_KEY_LOGGED_IN = "is_logged_in"
        const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Servicios base
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance(DATABASE_URL_USERS).reference
        roomRepository = RoomRepository(applicationContext)
        synchronizer = Synchronizer(roomRepository)

        // Si ya está logueado (legacy o sync), entra directo a Main
        sharedPreferences = getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE)
        val isLoggedInLegacy = sharedPreferences.getBoolean(LEGACY_KEY_LOGGED_IN, false)
        val isLoggedInSync = getSharedPreferences(SYNC_PREFS, MODE_PRIVATE).getBoolean(SYNC_KEY_LOGGED_IN, false)
        if (isLoggedInLegacy || isLoggedInSync) {
            startActivity(Intent(this, ActivityMain::class.java))
            finish()
            return
        }

        // UI
        setContentView(R.layout.login)
        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        showPasswordIcon = findViewById(R.id.password_icon)
        signInButton = findViewById(R.id.sign_in_button)
        registerButton = findViewById(R.id.register_button)
        forgotPasswordButton = findViewById(R.id.forgot_password_button)

        signInButton.setOnClickListener { signIn() }
        registerButton.setOnClickListener { startActivity(Intent(this, RegistroActivity::class.java)) }
        forgotPasswordButton.setOnClickListener { forgotPassword() }

        // Mostrar/ocultar contraseña manteniendo cursor al final
        showPasswordIcon.setOnClickListener {
            val isVisible = passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordEditText.inputType =
                if (isVisible) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordEditText.setSelection(passwordEditText.text?.length ?: 0)
            showPasswordIcon.setImageResource(if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            // (Se elimina la línea que reinicializaba un repo inexistente aquí)
        }
    }

    /**
     * crea el usuario
     */

    private fun createUserRtdbIfMissing(uid: String, email: String) {
        val userRef = FirebaseDatabase.getInstance(DATABASE_URL_USERS)
            .reference.child("usuarios").child(uid)

        userRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val userMap = mapOf(
                        "uid" to uid,
                        "email" to email,
                        "nombre" to "",
                        "apellidos" to "",
                        "cedula" to "",
                        "subregion" to "",   // <- debe llenarse luego o en backoffice
                        "agencia" to "",
                        "placaVehiculo" to "",
                        "telefono" to "",
                        "password" to ""      // si no lo guardas, déjalo vacío
                    )
                    userRef.setValue(userMap)
                }
            }
            .addOnFailureListener {
                // log opcional
            }
    }




    /**
     * Inicia sesión con FirebaseAuth y continua el flujo de sincronización.
     */
    private fun signIn() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        if (email.isEmpty()) { emailEditText.error = "El correo es requerido"; return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Formato de correo inválido"; return
        }
        if (password.isEmpty()) { passwordEditText.error = "La contraseña es requerida"; return }

        signInButton.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                signInButton.isEnabled = true
                if (!task.isSuccessful) { handleAuthError(task.exception); return@addOnCompleteListener }

                // NO mostramos Toast todavía
                markLoggedIn()

                val uid = auth.currentUser?.uid ?: run {
                    Toast.makeText(this, "No se pudo obtener el UID del usuario.", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                val dlg = SyncDialogFragment.show(supportFragmentManager).apply {
                    setHeader("Sincronizando…")
                    update(0, 0, "Preparando…")
                }

                lifecycleScope.launch {
                    try {
                        // 1) Traer o crear perfil en BD
                        val user = withContext(Dispatchers.IO) {
                            try {
                                roomRepository.upsertUserFromFirebase(uid) // Trae y guarda en Room desde RTDB
                            } catch (notFound: Exception) {
                                // Si no existe en RTDB, lo creamos mínimo y reintentamos
                                createUserRtdbIfMissing(uid, email)
                                roomRepository.upsertUserFromFirebase(uid)
                            }
                        }


                        // 2) Sincronizar subregión del usuario
                        val subId = user.subregion
                        synchronizer.syncSubregion(
                            subId,
                            onSyncStart = { /* ya está el modal */ },
                            onSyncProgress = { done, total, msg ->
                                if (!isFinishing && !isDestroyed) runOnUiThread { dlg.update(done, total, msg ?: "") }
                            },
                            onSyncSuccess = {
                                if (!isFinishing && !isDestroyed) {
                                    dlg.dismissAllowingStateLoss()
                                    // AHORA sí: éxito real
                                    Toast.makeText(this@LoginActivity, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
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
     * Marca el estado de login en las dos variantes de SharedPreferences.
     * - LEGACY_PREFS/LEGACY_KEY_LOGGED_IN  -> compatibilidad con código existente
     * - SYNC_PREFS/SYNC_KEY_LOGGED_IN      -> lo que lee el Synchronizer
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
     * Recupera datos básicos del usuario en RTDB para log/debug o precargar UI.
     * No escribe en DB local (eso lo hace el Synchronizer/FirebaseSyncManager).
     */
    private fun loadUserData(email: String) {
        // En RTDB “usuarios” se busca por campo "email"
        database.child("usuarios")
            .orderByChild("email")
            .equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(TAG, "Usuario no encontrado en RTDB para $email")
                        return
                    }
                    // Obtén el primer match
                    val first = snapshot.children.firstOrNull()
                    val nombre = first?.child("nombre")?.getValue(String::class.java).orEmpty()
                    val apellidos = first?.child("apellidos")?.getValue(String::class.java).orEmpty()
                    val placa = first?.child("placaVehiculo")?.getValue(String::class.java).orEmpty()
                    val subR = first?.child("subRegion")?.getValue(String::class.java).orEmpty()

                    Log.d(TAG, "Usuario RTDB -> $nombre $apellidos, placa=$placa, email=$email")
                    // Si quisieras empujar a un VM/UI, puedes hacerlo aquí.
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al leer RTDB usuario: ${error.message}")
                }
            })
    }

    /**
     * Maneja los mensajes de error más comunes de Auth.
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
     * Flujo de recuperación de contraseña (placeholder).
     * Puedes implementar Firebase Auth sendPasswordResetEmail aquí.
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

