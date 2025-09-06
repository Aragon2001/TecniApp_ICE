package com.Arasoftsolutions.tecniapp_ice.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.ui.modal.SyncDialogFragment
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val vm: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Define la subregión activa (TODO: cámbialo a tu fuente real: Room/Prefs)
        val subregion = "Guápiles"
        vm.setSubregion(subregion)

        // 2) Observa datos (ejemplo) y actualiza tu UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.medidores.collect { lista ->
                    // TODO: actualizar tu Recycler/Adapter con 'lista'
                }
            }
        }

        // 3) Lanza la sincronización mostrando el modal
        sincronizarConModal(subregion)
    }

    private fun sincronizarConModal(subregion: String) {
        // Mostrar el modal anclado a ESTE fragment
        val dlg = SyncDialogFragment.show(childFragmentManager)
        dlg.setHeader("Sincronizando…")
        dlg.update(0, 0, "Preparando…")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Llama al ViewModel y reporta progreso al diálogo
                vm.refresh(
                    onProgress = { done, total, msg ->
                        if (isAdded) {
                            dlg.update(done, total, msg)
                        }
                    }
                )

                // Si seguimos montados, cerrar el modal
                if (isAdded) {
                    runCatching { dlg.dismissAllowingStateLoss() }
                }
            } catch (t: Throwable) {
                if (!isAdded) return@launch
                // Muestra error y ofrece reintento seguro
                dlg.dismissWithError(t.localizedMessage) {
                    if (isAdded) sincronizarConModal(subregion)
                }
            }
        }
    }
}
