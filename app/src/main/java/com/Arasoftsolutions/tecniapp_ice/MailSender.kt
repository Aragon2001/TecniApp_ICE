package com.Arasoftsolutions.tecniapp_ice

import android.os.AsyncTask
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailSender(private val email: String, private val password: String) {

    fun sendMail(subject: String, message: String, recipient: String) {
        SendMailTask().execute(subject, message, recipient)
    }

    private inner class SendMailTask : AsyncTask<String, Void, Void>() {
        override fun doInBackground(vararg params: String?): Void? {
            val subject = params[0]
            val message = params[1]
            val recipient = params[2]

            try {
                val props = Properties()
                props["mail.smtp.host"] = "smtp.office365.com" // Cambiado para Outlook
                props["mail.smtp.port"] = "587"
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.starttls.enable"] = "true"

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(email, password)
                    }
                })

                val mimeMessage = MimeMessage(session)
                mimeMessage.setFrom(InternetAddress(email))
                mimeMessage.setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
                mimeMessage.subject = subject

                // Aquí indicamos que el contenido es HTML
                mimeMessage.setContent(message, "text/html; charset=utf-8")

                Transport.send(mimeMessage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
