package com.Arasoftsolutions.tecniapp_ice.registro

import androidx.lifecycle.ViewModel

class RegistroViewModel : ViewModel() {
    // Propiedades para almacenar datos del registro
    private var verificationCode: String? = null
    private var email: String? = null
    private var telefono: String? = null
    private var password: String? = null
    private var nombre: String? = null
    private var apellidos: String? = null
    private var cedula: String? = null
    private var subregion: String? = null
    private var agencia: String? = null
    private var placa: String? = null

    // Métodos para manejar el código de verificación
    fun setVerificationCode(code: String) {
        verificationCode = code
    }

    fun getVerificationCode(): String? {
        return verificationCode
    }

    // Métodos para manejar el email
    fun setEmail(email: String) {
        this.email = email
    }

    fun getEmail(): String? {
        return email
    }

    // Métodos para manejar el teléfono
    fun setTelefono(telefono: String) {
        this.telefono = telefono
    }

    fun getTelefono(): String? {
        return telefono
    }

    // Métodos para manejar la contraseña
    fun setPassword(password: String) {
        this.password = password
    }

    fun getPassword(): String? {
        return password
    }

    // Métodos para manejar los datos del usuario
    fun setNombre(nombre: String) {
        this.nombre = nombre
    }

    fun getNombre(): String? {
        return nombre
    }

    fun setApellidos(apellidos: String) {
        this.apellidos = apellidos
    }

    fun getApellidos(): String? {
        return apellidos
    }

    fun setCedula(cedula: String) {
        this.cedula = cedula
    }

    fun getCedula(): String? {
        return cedula
    }

    fun setSubregion(subregion: String) {
        this.subregion = subregion
    }

    fun getSubregion(): String? {
        return subregion
    }

    fun setAgencia(agencia: String) {
        this.agencia = agencia
    }

    fun getAgencia(): String? {
        return agencia
    }

    fun setPlaca(placa: String) {
        this.placa = placa
    }

    fun getPlaca(): String? {
        return placa
    }


fun setDatosTecnico(firstName: String, lastName: String, cedula: String) {
    this.nombre = firstName
    this.apellidos = lastName
    this.cedula = cedula
}

    }

    fun setDatosAdicionales(name: String, lastName: String, idCard: String) {

    }

