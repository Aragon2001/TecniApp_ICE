import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

class MailSender {

    data class MailAttachment(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    private var email: String = ""
    private var password: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendFormattedMail(
        subject: String,
        body: String,
        to: String,
        attachment: MailAttachment? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        val mailConfig = MailConfig()

        mailConfig.fetchMailCredentials({ remoteEmail, remotePassword ->
            email = remoteEmail
            password = remotePassword

            Log.d("MailSender", "Credenciales obtenidas: Email - $email")

            if (!isValidEmail(to)) {
                val errorMessage = "El formato del correo destinatario es inválido: $to"
                Log.e("MailSender", errorMessage)
                postCallback(callback, false, errorMessage)
                return@fetchMailCredentials
            }

            Thread {
                val (isSuccess, error) = sendHtmlMail(subject, body, to, attachment)
                postCallback(callback, isSuccess, error)
            }.start()
        }, { errorMessage ->
            Log.e("MailSender", "Error al obtener las credenciales: $errorMessage")
            postCallback(callback, false, "Error al obtener las credenciales: $errorMessage")
        })
    }

    private fun postCallback(callback: (Boolean, String?) -> Unit, success: Boolean, error: String?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(success, error)
        } else {
            mainHandler.post { callback(success, error) }
        }
    }

    private fun sendHtmlMail(
        subject: String,
        body: String,
        to: String,
        attachment: MailAttachment?
    ): Pair<Boolean, String?> {
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
                setFrom(InternetAddress(email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
            }

            val multipart = MimeMultipart()

            val bodyPart = MimeBodyPart().apply {
                setContent(body, "text/html; charset=utf-8")
            }
            multipart.addBodyPart(bodyPart)

            if (attachment != null) {
                val attachmentPart = MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(attachment.bytes, attachment.mimeType))
                    fileName = attachment.fileName
                }
                multipart.addBodyPart(attachmentPart)
            }

            message.setContent(multipart)
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


