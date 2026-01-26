package com.Arasoftsolutions.tecniapp_ice.ui.modal

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

class SyncDialogFragment : DialogFragment() {
    private var tvHeader: TextView? = null
    private var tvMessage: TextView? = null
    private var tvCounter: TextView? = null
    private var tvBytes: TextView? = null
    private var progressBar: LinearProgressIndicator? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_sync_progress, null)
        tvHeader = view.findViewById(R.id.tvHeader)
        tvMessage = view.findViewById(R.id.tvMessage)
        tvCounter = view.findViewById(R.id.tvCounter)
        tvBytes = view.findViewById(R.id.tvBytes)
        progressBar = view.findViewById(R.id.progressBar)

        val header = arguments?.getString(ARG_HEADER)
        val status = arguments?.getString(ARG_STATUS)

        tvHeader?.text = header
        tvMessage?.text = status

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
    }

    fun setHeader(text: String) {
        tvHeader?.text = text
    }

    fun update(done: Int, total: Int, msg: String?, downloadedBytes: Long = 0L) {
        tvMessage?.text = msg.orEmpty()
        val counter = if (total <= 0) {
            "—"
        } else {
            val safeTotal = total.coerceAtLeast(1)
            val safeDone = done.coerceAtLeast(0).coerceAtMost(safeTotal)
            val percent = (safeDone.toDouble() / safeTotal.toDouble()) * 100
            val percentLabel = String.format(Locale.getDefault(), "%.2f", percent)
            val progressValue = (percent * 100).toInt().coerceIn(0, 10000)
            progressBar?.setProgressCompat(progressValue, true)
            "$safeDone / $safeTotal • $percentLabel%"
        }
        tvCounter?.text = counter
        val mb = downloadedBytes.coerceAtLeast(0L).toDouble() / (1024 * 1024).toDouble()
        tvBytes?.text = String.format(Locale.getDefault(), "%.2f MB", mb)
    }

    /**
     * Versión nueva: recibe el Throwable para ver el error real.
     */
    fun dismissWithError(error: Throwable, retry: () -> Unit) {
        // Cerrar el diálogo de progreso sin crashear si ya no está en pantalla
        runCatching { dismissAllowingStateLoss() }

        val ctx = activity ?: return

        // Log detallado en consola (Logcat)
        Log.e(
            "SyncDialogFragment",
            "Error durante la sincronización: ${error.message ?: "sin mensaje"}",
            error
        )

        // Armar un mensaje un poco más explicativo para el usuario
        val userMessage = buildString {
            appendLine("Ocurrió un error durante la sincronización.")
            appendLine()
            appendLine("Detalle:")
            appendLine(error.message ?: error.toString())
            // Si quieres ver más detalle en pantalla, puedes agregar algunas líneas del stacktrace:
            error.stackTrace.take(3).forEach { st ->
                appendLine("at $st")
            }
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.sync_error_title)
            .setMessage(userMessage)
            .setPositiveButton(R.string.sync_retry) { _, _ -> retry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Versión vieja por compatibilidad, por si en algún lado solo le pasas un String.
     */
    fun dismissWithError(message: String?, retry: () -> Unit) {
        runCatching { dismissAllowingStateLoss() }
        val ctx = activity ?: return

        Log.e("SyncDialogFragment", "Error durante la sincronización: $message")

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.sync_error_title)
            .setMessage(message.orEmpty())
            .setPositiveButton(R.string.sync_retry) { _, _ -> retry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_HEADER = "header"
        private const val ARG_STATUS = "status"

        fun newInstance(header: String, status: String): SyncDialogFragment {
            return SyncDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_HEADER, header)
                    putString(ARG_STATUS, status)
                }
            }
        }

        fun show(fm: FragmentManager): SyncDialogFragment {
            val f = SyncDialogFragment()
            f.isCancelable = false
            f.show(fm, "sync_dialog")
            return f
        }
    }
}
