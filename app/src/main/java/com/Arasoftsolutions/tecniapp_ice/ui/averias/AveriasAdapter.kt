package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

class AveriasAdapter(
    private val onVerDetalle: (AveriaUI) -> Unit,
    private val onVerMapa: (AveriaUI) -> Unit,
    private val onAsignar: (AveriaUI) -> Unit,
    private val onAtender: (AveriaUI) -> Unit,
    private val onResolver: (AveriaUI) -> Unit,
    private val onRevertir: (AveriaUI) -> Unit,
) : RecyclerView.Adapter<AveriasAdapter.VH>() {

    private val items = mutableListOf<AveriaUI>()

    var currentUserUid: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var currentUserRegion: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var currentUserVehiculo: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private fun normalizeRegion(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9]".toRegex(), "")
    }

    fun submitList(newItems: List<AveriaUI>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition].id == newItems[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition] == newItems[newItemPosition]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_averia, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val viewEstadoIndicator: View = view.findViewById(R.id.viewEstadoIndicator)
        private val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        private val chipEstado: Chip = view.findViewById(R.id.chipEstado)
        private val btnCompartir: ImageButton = view.findViewById(R.id.btnCompartir)
        // Detalles
        private val tvCausa: TextView = view.findViewById(R.id.tvCausa)
        private val tvObs: TextView = view.findViewById(R.id.tvObs)
        private val tvCausaTecnico: TextView = view.findViewById(R.id.tvCausaTecnico)
        private val tvObsTecnico: TextView = view.findViewById(R.id.tvObsTecnico)
        // Asignación
        private val tvAsignado: TextView = view.findViewById(R.id.tvAsignado)
        private val tvAtendido: TextView = view.findViewById(R.id.tvAtendido)
        private val tvVehiculo: TextView = view.findViewById(R.id.tvVehiculo)
        private val tvNise: TextView = view.findViewById(R.id.tvNise)
        private val tvRegion: TextView = view.findViewById(R.id.tvRegion)
        private val tvAgencia: TextView = view.findViewById(R.id.tvAgencia)
        // Cliente y ubicación
        private val tvCliente: TextView? = view.findViewById(R.id.tvCliente)
        private val tvMedidor: TextView? = view.findViewById(R.id.tvMedidor)
        private val tvLocalizacion: TextView? = view.findViewById(R.id.tvLocalizacion)
        private val containerDireccion: View = view.findViewById(R.id.containerDireccion)
        private val tvDireccion: TextView? = view.findViewById(R.id.tvDireccion)
        // Kilometraje
        private val containerKm: View = view.findViewById(R.id.containerKm)
        private val tvKmInicial: TextView = view.findViewById(R.id.tvKmInicial)
        private val tvKmLlegada: TextView = view.findViewById(R.id.tvKmLlegada)
        private val tvKmFinal: TextView = view.findViewById(R.id.tvKmFinal)
        // Fechas
        private val tvFechaEvento: TextView = view.findViewById(R.id.tvFechaEvento)
        private val tvFechaInicio: TextView = view.findViewById(R.id.tvFechaInicio)
        private val tvFechaLlegada: TextView = view.findViewById(R.id.tvFechaLlegada)
        private val tvFechaFin: TextView = view.findViewById(R.id.tvFechaFin)
        // Mapa y botones
        private val mapContainer: View = view.findViewById(R.id.cardMapa)
        private val imgMapa: ImageView = view.findViewById(R.id.imgMapa)
        private val btnVerMapa: MaterialButton = view.findViewById(R.id.btnVerMapa)
        private val btnAsignar: MaterialButton = view.findViewById(R.id.btnAsignar)
        private val btnAtender: MaterialButton = view.findViewById(R.id.btnAtender)
        private val btnResolver: MaterialButton = view.findViewById(R.id.btnResolver)

        fun bind(item: AveriaUI) {
            val context = itemView.context
            val empty = "—"

            // ── Lógica de pertenencia y bloqueo ──────────────────────────────
            val currentUid = currentUserUid
            val bloqueadaPorClor = item.estadoClor.equals("RESUELTA", ignoreCase = true)
            val estadoEnum = Estado.fromLabel(item.estado)
            val ownerUid = item.ownerUidFor(estadoEnum)
            val placaUsuario = currentUserVehiculo?.trim()
            val placaAveria = item.vehiculo?.trim().orEmpty()
            val pertenecePorVehiculo =
                placaUsuario != null &&
                placaAveria.isNotBlank() &&
                placaUsuario.equals(placaAveria, ignoreCase = true)
            val asignadaAOtro =
                if (!ownerUid.isNullOrBlank()) {
                    currentUid == null || (ownerUid != currentUid && !pertenecePorVehiculo)
                } else {
                    placaAveria.isNotBlank() && !pertenecePorVehiculo
                }
            val pertenece = if (estadoEnum == Estado.ANULADA && ownerUid.isNullOrBlank()) {
                true
            } else {
                !asignadaAOtro && (!ownerUid.isNullOrBlank() || pertenecePorVehiculo)
            }
            val regionMismatch = currentUserRegion?.let { regionUsuario ->
                val regionAveria = item.region.trim()
                if (regionAveria.isBlank()) false
                else {
                    val userKey = normalizeRegion(regionUsuario)
                    val averiaKey = normalizeRegion(regionAveria)
                    userKey.isNotBlank() && averiaKey.isNotBlank() &&
                        !userKey.contains(averiaKey) && !averiaKey.contains(userKey)
                }
            } ?: false
            val readOnly = bloqueadaPorClor || asignadaAOtro || regionMismatch

            // ── Encabezado ────────────────────────────────────────────────────
            tvTitulo.text = item.descripcion

            val estadoParaColor = if (bloqueadaPorClor) Estado.RESUELTA else estadoEnum
            chipEstado.text = when {
                bloqueadaPorClor -> "Resuelta (CLOR)"
                asignadaAOtro && estadoEnum == Estado.ASIGNADA -> "Asignada (Otro técnico)"
                else -> item.estado
            }
            chipEstado.isCheckable = false
            chipEstado.isClickable = false

            val chipColor = when (estadoParaColor) {
                Estado.PENDIENTE -> ContextCompat.getColor(context, R.color.chip_pendiente)
                Estado.ASIGNADA -> ContextCompat.getColor(context, R.color.chip_asignada)
                Estado.EN_ATENCION -> ContextCompat.getColor(context, R.color.chip_en_atencion)
                Estado.RESUELTA -> ContextCompat.getColor(context, R.color.chip_resuelta)
                Estado.ANULADA -> ContextCompat.getColor(context, R.color.chip_anulada)
            }
            chipEstado.chipBackgroundColor = ColorStateList.valueOf(chipColor)
            chipEstado.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            viewEstadoIndicator.setBackgroundColor(chipColor)

            btnCompartir.setOnClickListener { shareCard(item) }

            // ── Detalles (CLOR y Técnico) ──────────────────────────────────
            tvCausa.text = item.causaClor?.takeIf { it.isNotBlank() } ?: empty
            tvObs.text = item.observacionesClor?.takeIf { it.isNotBlank() } ?: empty
            tvCausaTecnico.text = item.causa.takeIf { it.isNotBlank() } ?: empty
            tvObsTecnico.text = item.observaciones.takeIf { it.isNotBlank() } ?: empty

            // ── Asignación ────────────────────────────────────────────────────
            tvAsignado.text = if (item.tecnico.isBlank()) {
                context.getString(R.string.averia_sin_asignar)
            } else {
                item.tecnico
            }
            tvAtendido.text = item.resolvedAtendidoDisplay(empty)
            tvVehiculo.text = item.vehiculo?.takeIf { it.isNotBlank() } ?: empty
            tvNise.text = item.nise.takeIf { it.isNotBlank() } ?: empty
            tvRegion.text = item.region.takeIf { it.isNotBlank() } ?: empty
            tvAgencia.text = item.agencia.takeIf { it.isNotBlank() } ?: empty

            // ── Cliente y ubicación ────────────────────────────────────────
            tvCliente?.text = when (item.tipoAfectacion) {
                TipoAfectacion.CLIENTE -> buildList {
                    add(context.getString(R.string.averia_tipo_cliente))
                    item.cliente?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" • ")
                TipoAfectacion.SECTOR -> context.getString(R.string.averia_tipo_sector)
            }
            tvMedidor?.text = item.numeroMedidor?.takeIf { it.isNotBlank() } ?: empty

            tvLocalizacion?.text = buildList {
                item.localizacion?.takeIf { it.isNotBlank() }?.let { add(it) }
                item.medidorPueblo?.takeIf { it.isNotBlank() }?.let {
                    add(context.getString(R.string.averia_medidor_pueblo, it))
                }
                item.medidorCalle?.takeIf { it.isNotBlank() }?.let {
                    add(context.getString(R.string.averia_medidor_calle, it))
                }
                item.medidorPoste?.takeIf { it.isNotBlank() }?.let {
                    add(context.getString(R.string.averia_medidor_poste, it))
                }
                item.medidorMetros?.takeIf { it.isNotBlank() }?.let {
                    add(context.getString(R.string.averia_medidor_metros, it))
                }
            }.joinToString(" • ").ifBlank { empty }

            val hasDireccion = !item.direccion.isNullOrBlank()
            containerDireccion.isVisible = hasDireccion
            if (hasDireccion) tvDireccion?.text = item.direccion

            // ── Kilometraje ────────────────────────────────────────────────
            val kmI = item.kilometrajeInicio
            val kmL = item.kilometrajeLlegada
            val kmF = item.kilometrajeFinal
            containerKm.isVisible = kmI != null || kmL != null || kmF != null
            if (containerKm.isVisible) {
                tvKmInicial.text = kmI?.fmtKm() ?: empty
                tvKmLlegada.text = kmL?.fmtKm() ?: empty
                tvKmFinal.text = kmF?.fmtKm() ?: empty
            }

            // ── Fechas ────────────────────────────────────────────────────
            val fmtFull = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val fmtTime = DateFormat.getTimeInstance(DateFormat.SHORT)
            val fechaEvento = fmtFull.format(Date(item.fechaMillis))
            val inicioMs = item.horaInicio ?: item.horaAtencionInicio
            val llegadaMs = item.horaLlegada
            val finMs = item.horaFinal ?: item.horaAtencionFinal

            tvFechaEvento.text = fechaEvento
            tvFechaInicio.text = inicioMs?.let { fmtTime.format(Date(it)) } ?: empty
            tvFechaLlegada.text = llegadaMs?.let { fmtTime.format(Date(it)) } ?: empty
            tvFechaFin.text = finMs?.let { fmtTime.format(Date(it)) } ?: empty

            // ── Coordenadas y mapa ────────────────────────────────────────
            val lat = item.lat
            val lng = item.lng
            val hasCoords = !(lat == 0.0 && lng == 0.0)
            mapContainer.isVisible = hasCoords
            btnVerMapa.isVisible = hasCoords
            btnVerMapa.isEnabled = hasCoords
            if (hasCoords) {
                val mapLabel = item.direccion?.takeIf { it.isNotBlank() } ?: item.agencia
                val mapUrl = AveriaStaticMapProvider.buildUrl(context, lat, lng, mapLabel)
                if (mapUrl != null) AveriaStaticMapProvider.loadInto(context, imgMapa, mapUrl)
                else imgMapa.setImageResource(R.drawable.ic_map_placeholder)
                mapContainer.isClickable = true
                mapContainer.isFocusable = true
                mapContainer.setOnClickListener { onVerMapa(item) }
            } else {
                imgMapa.setImageResource(R.drawable.ic_map_placeholder)
                mapContainer.isClickable = false
                mapContainer.isFocusable = false
                mapContainer.setOnClickListener(null)
            }
            btnVerMapa.setOnClickListener { onVerMapa(item) }

            // ── Click raíz ────────────────────────────────────────────────
            if (regionMismatch) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                itemView.setOnClickListener { onVerDetalle(item) }
                itemView.isClickable = true
            }

            // ── Callbacks de botones ──────────────────────────────────────
            btnAsignar.setOnClickListener { onAsignar(item) }
            btnAtender.setOnClickListener { onAtender(item) }
            btnResolver.setOnClickListener { onResolver(item) }

            // ── Visibilidad de botones por estado ─────────────────────────
            when (estadoEnum) {
                Estado.PENDIENTE -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_asignar)
                        setIconResource(R.drawable.ic_person)
                        isEnabled = true; isVisible = true; alpha = 1f
                    }
                    btnAtender.apply {
                        text = context.getString(R.string.averia_atender)
                        setIconResource(R.drawable.ic_play_arrow)
                        isEnabled = true; isVisible = true; alpha = 1f
                    }
                    btnResolver.isVisible = false
                }
                Estado.ASIGNADA -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_eliminar_asignacion)
                        setIconResource(R.drawable.ic_person)
                        isEnabled = pertenece; isVisible = true
                        alpha = if (pertenece) 1f else 0.5f
                    }
                    btnAtender.apply {
                        text = context.getString(R.string.averia_atender)
                        setIconResource(R.drawable.ic_play_arrow)
                        isEnabled = pertenece; isVisible = true
                        alpha = if (pertenece) 1f else 0.5f
                    }
                    btnResolver.isVisible = false
                }
                Estado.EN_ATENCION -> {
                    btnAsignar.isVisible = false
                    btnAtender.apply {
                        text = context.getString(R.string.averia_cancelar_atencion)
                        setIconResource(R.drawable.ic_close_sheet)
                        isEnabled = pertenece; isVisible = true
                        alpha = if (pertenece) 1f else 0.5f
                    }
                    btnResolver.apply {
                        text = context.getString(R.string.averia_resolver)
                        setIconResource(R.drawable.ic_check)
                        isEnabled = pertenece; isVisible = true
                        alpha = if (pertenece) 1f else 0.5f
                    }
                }
                Estado.RESUELTA -> {
                    btnAsignar.isVisible = false
                    btnAtender.isVisible = false
                    btnResolver.apply {
                        text = context.getString(R.string.averia_exportar_pdf)
                        setIconResource(R.drawable.ic_check)
                        isEnabled = pertenece; isVisible = pertenece
                        alpha = if (pertenece) 1f else 0.45f
                    }
                }
                Estado.ANULADA -> {
                    btnAsignar.apply {
                        text = context.getString(R.string.averia_revertir_pendiente)
                        setIconResource(R.drawable.ic_person)
                        isEnabled = pertenece; isVisible = true
                        alpha = if (pertenece) 1f else 0.45f
                        setOnClickListener { onRevertir(item) }
                    }
                    btnAtender.isVisible = false
                    btnResolver.isVisible = false
                }
            }

            // ── Bloqueo final ─────────────────────────────────────────────
            if (regionMismatch) {
                btnAsignar.isVisible = false; btnAtender.isVisible = false; btnResolver.isVisible = false
            } else if (bloqueadaPorClor) {
                btnAsignar.isVisible = false; btnAtender.isVisible = false; btnResolver.isVisible = false
            } else if (asignadaAOtro) {
                btnAsignar.isEnabled = false; btnAtender.isEnabled = false; btnResolver.isEnabled = false
                btnAsignar.alpha = if (btnAsignar.isVisible) 0.45f else 1f
                btnAtender.alpha = if (btnAtender.isVisible) 0.45f else 1f
                btnResolver.alpha = if (btnResolver.isVisible) 0.45f else 1f
            } else {
                btnAsignar.alpha = 1f; btnAtender.alpha = 1f; btnResolver.alpha = 1f
            }

            // Usuario sin vehículo no puede atender en campo
            if (currentUserVehiculo.isNullOrBlank() && btnAtender.isVisible) {
                btnAtender.isEnabled = false
                btnAtender.alpha = 0.45f
            }

            @Suppress("UNUSED_VARIABLE")
            val _readOnly = readOnly
        }

        private fun shareCard(item: AveriaUI) {
            val context = itemView.context
            try {
                itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(itemView.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                itemView.layout(0, 0, itemView.measuredWidth, itemView.measuredHeight)
                val bitmap = Bitmap.createBitmap(
                    itemView.measuredWidth.coerceAtLeast(1),
                    itemView.measuredHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                itemView.draw(canvas)

                val shareDir = File(context.cacheDir, "share")
                shareDir.mkdirs()
                val safeName = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val file = File(shareDir, "averia_$safeName.png")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                bitmap.recycle()

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, item.descripcion)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir avería"))
            } catch (_: Exception) {
                Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Double.fmtKm(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else "%.1f".format(this)
}
