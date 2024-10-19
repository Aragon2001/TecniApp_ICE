package com.Arasoftsolutions.tecniapp_ice.ui.averias

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AveriasViewModel : ViewModel() {

    // Lista de averías
    private val _averias = MutableLiveData<List<Averia>>()

    // LiveData que observará el fragmento
    val averias: LiveData<List<Averia>> get() = _averias

    init {
        // Simular la carga inicial de datos
        loadAverias()
    }

    // Método para cargar las averías
    private fun loadAverias() {
        // Aquí puedes cargar los datos reales desde Firebase. Por ahora se usa un ejemplo de datos simulados.
        val averiasSimuladas = listOf(
            Averia("Cortocircuito en línea 2", "Carlos", "Marzo 13, 2023", "Alta", "Pendiente", "Carlos", "Lima, Perú"),
            Averia("Falla en poste", "Luis", "Marzo 14, 2023", "Media", "En progreso", "Roberto", "Bogotá, Colombia"),
            Averia("Corte de energía", "María", "Marzo 15, 2023", "Baja", "Resuelto", "Javier", "Quito, Ecuador")
        )

        // Actualiza la lista de averías
        _averias.value = averiasSimuladas
    }

    // Método para actualizar la lista de averías (puedes llamarlo cuando recibas nuevos datos de Firebase)
    fun updateAverias(nuevasAverias: List<Averia>) {
        _averias.value = nuevasAverias
    }
}
