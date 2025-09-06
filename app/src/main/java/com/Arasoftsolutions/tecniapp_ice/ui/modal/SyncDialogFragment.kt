package com.Arasoftsolutions.tecniapp_ice.ui.modal

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.TextView

class SyncDialogFragment : DialogFragment() {
    private lateinit var tvHeader: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvCounter: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_sync_progress, null)
        tvHeader = view.findViewById(R.id.tvHeader)
        tvMessage = view.findViewById(R.id.tvMessage)
        tvCounter = view.findViewById(R.id.tvCounter)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }


 fun setHeader(text: String) {
    view?.findViewById<TextView>(R.id.tvHeader)?.text = text
}

fun update(done: Int, total: Int, msg: String?) {
    view?.findViewById<TextView>(R.id.tvMessage)?.text = msg
    view?.findViewById<TextView>(R.id.tvCounter)?.text = "$done / $total"
}


  fun dismissWithError(message: String?, retry: () -> Unit) {
    // Intenta cerrar el diálogo sin romper si ya no está en pantalla
    runCatching { dismissAllowingStateLoss() }

    // Si el fragmento ya no está adjunto, no intentes mostrar otro diálogo
    val ctx = activity ?: return

    MaterialAlertDialogBuilder(ctx)
        .setTitle("Error de sincronización")
        .setMessage(message.orEmpty())
        .setPositiveButton("Reintentar") { _, _ -> retry() }
        .setNegativeButton("Cerrar", null)
        .show()
}


    companion object {
        fun show(fm: FragmentManager): SyncDialogFragment {
            val f = SyncDialogFragment()
            f.isCancelable = false
            f.show(fm, "sync_dialog")
            return f
        }
    }
}

