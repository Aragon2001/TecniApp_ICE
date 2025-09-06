package com.Arasoftsolutions.tecniapp_ice.registro

import androidx.lifecycle.ViewModel

/**
 * ViewModel utilizado durante el proceso de registro para almacenar
 * temporalmente la información del usuario entre los diferentes pasos.
 */
class RegistroViewModel : ViewModel() {

    // Datos de verificación
    private var verificationCode: String? = null

    // Credenciales de acceso
    private var email: String? = null
    private var telefono: String? = null
    private var password: String? = null

    // Datos personales
    private var nombre: String? = null
    private var apellidos: String? = null
    private var cedula: String? = null

    // Datos adicionales
    private var subregion: String? = null
    private var agencia: String? = null
    private var placa: String? = null

    // Código de verificación
    fun setVerificationCode(code: String) { verificationCode = code }
    fun getVerificationCode(): String? = verificationCode

    // Email
    fun setEmail(email: String) { this.email = email }
    fun getEmail(): String? = email

    // Teléfono
    fun setTelefono(telefono: String) { this.telefono = telefono }
    fun getTelefono(): String? = telefono

    // Contraseña
    fun setPassword(password: String) { this.password = password }
    fun getPassword(): String? = password

    // Nombre
    fun setNombre(nombre: String) { this.nombre = nombre }
    fun getNombre(): String? = nombre

    // Apellidos
    fun setApellidos(apellidos: String) { this.apellidos = apellidos }
    fun getApellidos(): String? = apellidos

    // Cédula
    fun setCedula(cedula: String) { this.cedula = cedula }
    fun getCedula(): String? = cedula

    // Subregión
    fun setSubregion(subregion: String) { this.subregion = subregion }
    fun getSubregion(): String? = subregion

    // Agencia
    fun setAgencia(agencia: String) { this.agencia = agencia }
    fun getAgencia(): String? = agencia

    // Placa del vehículo
    fun setPlaca(placa: String) { this.placa = placa }
    fun getPlaca(): String? = placa

    // Datos del técnico (Paso 3)
    fun setDatosTecnico(firstName: String, lastName: String, cedula: String) {
        this.nombre = firstName
        this.apellidos = lastName
        this.cedula = cedula
    }

    // Datos adicionales (Paso 4)
    fun setDatosAdicionales(subregion: String, agencia: String, placa: String) {
        this.subregion = subregion
        this.agencia = agencia
        this.placa = placa
    }
}

