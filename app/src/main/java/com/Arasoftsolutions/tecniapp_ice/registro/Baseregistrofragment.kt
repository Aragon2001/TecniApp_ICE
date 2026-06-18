package com.Arasoftsolutions.tecniapp_ice.registro

import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import androidx.fragment.app.Fragment

/**
 * Clase base para todos los pasos del registro.
 *
 * Centraliza:
 *  - animateProgress(): anima la barra de progreso al valor destino.
 *  - isFragmentAlive(): guarda de ciclo de vida para callbacks asíncronos.
 */
abstract class BaseRegistroFragment : Fragment() {

    /**
     * Anima la ProgressBar desde su valor actual hasta [targetProgress] en 400 ms.
     * Llamar desde onResume() de cada paso.
     *
     * Uso:
     *   animateProgress(view.findViewById(R.id.progressBar), 25)   // Paso 1
     *   animateProgress(view.findViewById(R.id.progressBar), 50)   // Paso 2
     *   animateProgress(view.findViewById(R.id.progressBar), 75)   // Paso 3
     *   animateProgress(view.findViewById(R.id.progressBar), 100)  // Paso 4
     */
    protected fun animateProgress(bar: ProgressBar, targetProgress: Int) {
        ObjectAnimator.ofInt(bar, "progress", bar.progress, targetProgress).apply {
            duration = 400L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    /**
     * FIX: guarda de ciclo de vida.
     * Usar al inicio de cualquier callback asíncrono (addOnSuccessListener,
     * addOnFailureListener, coroutine) antes de acceder a la UI o a
     * requireContext()/requireActivity().
     *
     * Ejemplo:
     *   .addOnSuccessListener {
     *       if (!isFragmentAlive()) return@addOnSuccessListener
     *       Toast.makeText(requireContext(), "OK", Toast.LENGTH_SHORT).show()
     *   }
     */
    protected fun isFragmentAlive(): Boolean = isAdded && !isDetached && context != null
}