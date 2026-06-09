package com.Arasoftsolutions.tecniapp_ice.ui.modal

import android.animation.ValueAnimator
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.fragment.app.FragmentManager

class SyncDialogFragment : BottomSheetDialogFragment() {

    private var tvHeader: TextView? = null
    private var tvMessage: TextView? = null
    private var tvCounter: TextView? = null
    private var tvPercent: TextView? = null
    private var tvBytes: TextView? = null
    private var tvElapsed: TextView? = null
    private var progressBar: LinearProgressIndicator? = null
    private var rainAnimator: ValueAnimator? = null
    private val startTimeMs = System.currentTimeMillis()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            isDraggable = false
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_sync_progress, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvHeader   = view.findViewById(R.id.tvHeader)
        tvMessage  = view.findViewById(R.id.tvMessage)
        tvCounter  = view.findViewById(R.id.tvCounter)
        tvPercent  = view.findViewById(R.id.tvPercent)
        tvBytes    = view.findViewById(R.id.tvBytes)
        tvElapsed  = view.findViewById(R.id.tvElapsed)
        progressBar = view.findViewById(R.id.progressBar)

        tvHeader?.text = arguments?.getString(ARG_HEADER) ?: ""
        tvMessage?.text = arguments?.getString(ARG_STATUS) ?: ""

        // Animación de gotas de lluvia
        val drops = listOf<View>(
            view.findViewById(R.id.rainDrop1),
            view.findViewById(R.id.rainDrop2),
            view.findViewById(R.id.rainDrop3),
            view.findViewById(R.id.rainDrop4),
            view.findViewById(R.id.rainDrop5)
        )
        startRainAnimation(drops)

        // Contador de tiempo transcurrido
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
                tvElapsed?.text = String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60, elapsed % 60)
            }
        }
    }

    private fun startRainAnimation(drops: List<View>) {
        val dropDurationMs = 700L
        val staggerMs = 140L
        val totalCycleMs = dropDurationMs + staggerMs * (drops.size - 1)  // 1260ms
        val density = resources.displayMetrics.density
        val travelPx = 36f * density

        rainAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = totalCycleMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val cycleMs = (va.animatedValue as Float) * totalCycleMs
                drops.forEachIndexed { i, drop ->
                    val slotStart = i * staggerMs.toFloat()
                    val slotEnd   = slotStart + dropDurationMs
                    if (cycleMs in slotStart..slotEnd) {
                        val p = ((cycleMs - slotStart) / dropDurationMs).toFloat()
                        drop.translationY = p * travelPx
                        drop.alpha = when {
                            p < 0.15f -> p / 0.15f * 0.9f      // aparece
                            p > 0.70f -> (1f - p) / 0.30f * 0.9f // desaparece
                            else -> 0.9f
                        }
                    } else {
                        drop.translationY = 0f
                        drop.alpha = 0f
                    }
                }
            }
            start()
        }
    }

    override fun onDestroyView() {
        rainAnimator?.cancel()
        rainAnimator = null
        tvHeader = null
        tvMessage = null
        tvCounter = null
        tvPercent = null
        tvBytes = null
        tvElapsed = null
        progressBar = null
        super.onDestroyView()
    }

    fun setHeader(text: String) { tvHeader?.text = text }

    fun update(done: Int, total: Int, msg: String?, downloadedBytes: Long = 0L) {
        tvMessage?.text = msg.orEmpty()
        if (total <= 0) {
            tvCounter?.text = "—"
            tvPercent?.text = "—"
            runCatching { progressBar?.isIndeterminate = true }
        } else {
            runCatching { progressBar?.isIndeterminate = false }
            val safeTotal = total.coerceAtLeast(1)
            val safeDone  = done.coerceAtLeast(0).coerceAtMost(safeTotal)
            val percent   = (safeDone.toDouble() / safeTotal.toDouble()) * 100.0
            progressBar?.setProgressCompat((percent * 100.0).toInt().coerceIn(0, 10000), true)
            tvCounter?.text = String.format(Locale.getDefault(), "Paso %d de %d", safeDone, safeTotal)
            tvPercent?.text = String.format(Locale.getDefault(), "%.1f%%", percent)
        }
        val mb = downloadedBytes.coerceAtLeast(0L).toDouble() / (1024.0 * 1024.0)
        tvBytes?.text = String.format(Locale.getDefault(), "%.2f MB", mb)
    }

    fun dismissWithError(error: Throwable, retry: () -> Unit) {
        runCatching { dismissAllowingStateLoss() }
        val ctx = activity ?: return
        Log.e("SyncDialogFragment", "Error durante la sincronización: ${error.message}", error)
        val msg = buildString {
            appendLine("Ocurrió un error durante la sincronización.")
            appendLine()
            appendLine("Detalle:")
            appendLine(error.message ?: error.toString())
            error.stackTrace.take(3).forEach { appendLine("at $it") }
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.sync_error_title)
            .setMessage(msg)
            .setPositiveButton(R.string.sync_retry) { _, _ -> retry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

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

        fun newInstance(header: String, status: String): SyncDialogFragment =
            SyncDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_HEADER, header)
                    putString(ARG_STATUS, status)
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
