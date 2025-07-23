import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class MailConfig {

    private val mFirebaseRemoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        // Configuración de Firebase Remote Config
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // Intervalo mínimo de fetch: 1 hora
            .build()
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings)
    }

    /**
     * Método para obtener las credenciales de correo desde Firebase Remote Config
     * @param onSuccess Callback que recibe el correo y la contraseña si se obtienen correctamente
     * @param onFailure Callback que recibe un mensaje de error si ocurre algún problema
     */
    fun fetchMailCredentials(onSuccess: (String, String) -> Unit, onFailure: (String) -> Unit) {
        mFirebaseRemoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Obtener valores de Remote Config
                    val email = mFirebaseRemoteConfig.getString("mail_sender_email")
                    val password = mFirebaseRemoteConfig.getString("mail_sender_password")

                    // Validar que las credenciales no estén vacías y que el correo tenga un formato válido
                    if (email.isNotEmpty() && password.isNotEmpty() &&
                        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                    ) {
                        // Callback de éxito con las credenciales
                        onSuccess(email, password)
                    } else {
                        // Error si las credenciales son inválidas o no existen
                        onFailure("Credenciales de correo no configuradas correctamente en Remote Config.")
                        Log.e("MailConfig", "Credenciales inválidas o no configuradas: Email=$email, Password=${password.isNotEmpty()}")
                    }
                } else {
                    // Manejo de error en el fetch
                    val errorMessage = task.exception?.message ?: "Error desconocido"
                    onFailure("Error al obtener Remote Config: $errorMessage")
                    Log.e("MailConfig", "Fetch fallido: $errorMessage")
                }
            }
    }
}
