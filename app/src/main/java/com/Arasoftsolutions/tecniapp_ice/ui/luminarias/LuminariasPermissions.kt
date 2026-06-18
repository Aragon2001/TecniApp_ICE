package com.Arasoftsolutions.tecniapp_ice.ui.luminarias

data class LuminariaPermisos(
    val verOtrosCamiones: Boolean,
    val puedeAtender: Boolean,
    val puedeEditarPendienteAdmin: Boolean,
    val puedeEditarReparadaTecnico: Boolean,
    val puedeAgregarMateriales: Boolean,
    val puedeRegistrarNuevoCaso: Boolean,
    val puedeDescargarMachote: Boolean,
    val puedeCargarMachote: Boolean,
    val puedeEnviarMachote: Boolean,
    val puedeAsignarCamion: Boolean,
    val puedeCambiarAReparada: Boolean,
    val puedeDevolverAPendiente: Boolean
)

object LuminariasPermissionResolver {

    fun resolverPara(rol: String): LuminariaPermisos {
        val norm = normalizar(rol)
        val esGestor = norm.contains("supervis") || norm.contains("admin")
        return if (esGestor) permisosGestor() else permisosTecnico()
    }

    private fun permisosTecnico() = LuminariaPermisos(
        verOtrosCamiones = false,
        puedeAtender = true,
        puedeEditarPendienteAdmin = false,
        puedeEditarReparadaTecnico = true,
        puedeAgregarMateriales = true,
        puedeRegistrarNuevoCaso = false,
        puedeDescargarMachote = false,
        puedeCargarMachote = false,
        puedeEnviarMachote = false,
        puedeAsignarCamion = false,
        puedeCambiarAReparada = true,
        puedeDevolverAPendiente = true
    )

    private fun permisosGestor() = LuminariaPermisos(
        verOtrosCamiones = true,
        puedeAtender = false,
        puedeEditarPendienteAdmin = true,
        puedeEditarReparadaTecnico = false,
        puedeAgregarMateriales = false,
        puedeRegistrarNuevoCaso = true,
        puedeDescargarMachote = true,
        puedeCargarMachote = true,
        puedeEnviarMachote = true,
        puedeAsignarCamion = true,
        puedeCambiarAReparada = false,
        puedeDevolverAPendiente = false
    )

    private fun normalizar(raw: String): String {
        val nfd = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFD)
        return nfd.replace("\\p{M}+".toRegex(), "").lowercase()
    }
}
