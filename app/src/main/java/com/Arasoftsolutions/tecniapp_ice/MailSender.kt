import android.util.Log
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.*

class MailSender {

    private var email: String = ""
    private var password: String = ""

    // Método para enviar correo con credenciales remotas
  fun sendFormattedMail(
    subject: String,
    body: String,
    to: String,
    callback: (Boolean, String?) -> Unit
) {
    val mailConfig = MailConfig()

    // Obtener las credenciales remotas
    mailConfig.fetchMailCredentials({ remoteEmail, remotePassword ->
        email = remoteEmail
        password = remotePassword

        Log.d("MailSender", "Credenciales obtenidas: Email - $email y Password: $password")

        // Validar el formato del correo destino
        if (!isValidEmail(to)) {
            val errorMessage = "El formato del correo destinatario es inválido: $to"
            Log.e("MailSender", errorMessage)
            callback(false, errorMessage)
            return@fetchMailCredentials
        }

        // Ejecutar el envío de correo en un hilo secundario
        Thread {
            val (isSuccess, error) = sendHtmlMail(subject, body, to)
            // Volver al hilo principal para ejecutar el callback
            callback(isSuccess, error)
        }.start()

    }, { errorMessage ->
        Log.e("MailSender", "Error al obtener las credenciales: $errorMessage")
        callback(false, "Error al obtener las credenciales: $errorMessage")
    })
}


    // Método para enviar correos en formato HTML
    private fun sendHtmlMail(subject: String, body: String, to: String): Pair<Boolean, String?> {
        return try {
            val properties = Properties().apply {
                put("mail.smtp.host", "smtp.gmail.com") // Servidor SMTP de Gmail
                put("mail.smtp.port", "587") // Puerto TLS
                put("mail.smtp.auth", "true") // Habilitar autenticación
                put("mail.smtp.starttls.enable", "true") // Habilitar STARTTLS
            }

            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(email, password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(email)) // Remitente
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to)) // Destinatario
                this.subject = subject
                setContent(body, "text/html; charset=utf-8") // Configura contenido HTML
            }

            Transport.send(message)
            Log.d("MailSender", "Correo enviado con éxito.")
            true to null // Éxito

        } catch (e: MessagingException) {
            val error = "Error al enviar el correo: ${e.message}. StackTrace: ${e.stackTraceToString()}"
            Log.e("MailSender", error)
            false to error

        } catch (e: Exception) {
            val error = "Error inesperado: ${e.message}. StackTrace: ${e.stackTraceToString()}"
            Log.e("MailSender", error)
            false to error
        }
    }

    // Validar el formato del correo electrónico
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}


