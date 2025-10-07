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
    private var primerApellido: String? = null
    private var segundoApellido: String? = null
    private var cedula: String? = null

    // Datos adicionales
    private var region: String? = null
    private var regionNombre: String? = null
    private var subregion: String? = null
    private var subregionNombre: String? = null
    private var agenciaId: String? = null
    private var agenciaNombre: String? = null
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
    fun setApellidos(primer: String, segundo: String) {
        primerApellido = primer
        segundoApellido = segundo
    }
    fun getPrimerApellido(): String? = primerApellido
    fun getSegundoApellido(): String? = segundoApellido
    fun getApellidosCompletos(): String? {
        val parts = listOfNotNull(primerApellido?.takeIf { it.isNotBlank() }, segundoApellido?.takeIf { it.isNotBlank() })
        val joined = parts.joinToString(" ").trim()
        return joined.ifBlank { null }
    }

    // Cédula
    fun setCedula(cedula: String) { this.cedula = cedula }
    fun getCedula(): String? = cedula

    // Región y subregión
    fun setRegion(id: String, nombre: String) {
        region = id
        regionNombre = nombre
    }
    fun getRegion(): String? = region
    fun getRegionNombre(): String? = regionNombre

    fun setSubregion(subregion: String, nombre: String) {
        this.subregion = subregion
        this.subregionNombre = nombre
    }
    fun getSubregion(): String? = subregion
    fun getSubregionNombre(): String? = subregionNombre

    // Agencia
    fun setAgencia(id: String?, nombre: String) {
        agenciaId = id
        agenciaNombre = nombre
        agencia = nombre
    }
    fun getAgencia(): String? = agencia
    fun getAgenciaId(): String? = agenciaId
    fun getAgenciaNombre(): String? = agenciaNombre

    // Placa del vehículo
    fun setPlaca(placa: String) { this.placa = placa }
    fun getPlaca(): String? = placa

    // Datos del técnico (Paso 3)
    fun setDatosTecnico(firstName: String, lastName: String, secondLastName: String, cedula: String) {
        this.nombre = firstName
        this.primerApellido = lastName
        this.segundoApellido = secondLastName
        this.cedula = cedula
    }

    // Datos adicionales (Paso 4)
    fun setDatosAdicionales(
        regionId: String,
        regionNombre: String,
        subregionId: String,
        subregionNombre: String,
        agenciaId: String?,
        agenciaNombre: String,
        placa: String
    ) {
        this.region = regionId
        this.regionNombre = regionNombre
        this.subregion = subregionId
        this.subregionNombre = subregionNombre
        this.agenciaId = agenciaId
        this.agenciaNombre = agenciaNombre
        this.agencia = agenciaNombre
        this.placa = placa
    }
}

