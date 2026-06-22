package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Arasoftsolutions.tecniapp_ice.Database.entities.AveriaEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.AppDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAveriasMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AveriasMapFragment : Fragment(), OnMapReadyCallback {

    private var _b: FragmentAveriasMapBinding? = null
    private val b get() = _b!!

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val markerToAveria = mutableMapOf<String, AveriaEntity>()
    private var selectedMarker: Marker? = null

    private val costaRicaCenter = LatLng(9.7489, -83.7534)
    private val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())

    private val repo by lazy { AveriasRepository(AppDatabase.getInstance(requireContext())) }
    private val roomRepo by lazy { RoomRepository.getInstance(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentAveriasMapBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        b.btnMarkerCerrar.setOnClickListener { hideMarkerCard() }
        b.fabMiUbicacion.setOnClickListener { centerOnMyLocation() }

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(costaRicaCenter, 8f))

        if (hasLocationPermission()) {
            @Suppress("MissingPermission")
            googleMap.isMyLocationEnabled = true
        }

        googleMap.setOnMarkerClickListener { marker ->
            selectedMarker?.setIcon(markerIcon(markerToAveria[selectedMarker?.id]?.estado ?: ""))
            selectedMarker = marker
            val averia = markerToAveria[marker.id]
            if (averia != null) {
                marker.setIcon(markerIconSelected(averia.estado))
                showMarkerCard(averia)
            }
            true
        }

        googleMap.setOnMapClickListener {
            hideMarkerCard()
            selectedMarker?.setIcon(markerIcon(markerToAveria[selectedMarker?.id]?.estado ?: ""))
            selectedMarker = null
        }

        observeAverias()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun centerOnMyLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && ::googleMap.isInitialized) {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 14f)
                    )
                }
            }
        } catch (_: SecurityException) { }
    }

    private fun observeAverias() {
        val cutoff = System.currentTimeMillis() - twoDaysMillis
        viewLifecycleOwner.lifecycleScope.launch {
            // Obtener agencias preferidas del usuario
            val preferredRaw = AveriaNotificationPreferences.getSelectedAgencies(requireContext())
            val preferredNorm = preferredRaw.map { normalizeAveriaText(it) }.toSet()

            // Si no hay preferidas, usar agencia asignada del perfil
            val fallbackAgency: String? = if (preferredNorm.isEmpty()) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    val user = roomRepo.observarUsuario(uid).first()
                    user?.agencia?.takeIf { it.isNotBlank() }
                        ?: user?.agenciaId?.takeIf { it.isNotBlank() }
                } else null
            } else null

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observe(emptyList(), "", "", "").collectLatest { averias ->
                    val filtered = averias.filter { av ->
                        // Respetar estadoClor RESUELTA aunque el campo estado legacy diga otra cosa
                        val est = if (av.estadoClor?.equals("RESUELTA", ignoreCase = true) == true)
                            Estado.RESUELTA
                        else
                            Estado.fromLabel(av.estado)

                        // Para resueltas, usar el tiempo de cierre real (no el de apertura del incidente)
                        val tiempoCierre = listOfNotNull(
                            av.horaFinalMillis,
                            av.atencionHoraFinalMillis,
                            av.lastUpdated.takeIf { it > 0 }
                        ).maxOrNull()

                        val pasaFecha = when (est) {
                            Estado.PENDIENTE -> true
                            Estado.RESUELTA  -> (tiempoCierre ?: av.fechaInicioMillis) >= cutoff
                            else             -> false
                        }
                        if (!pasaFecha) return@filter false

                        // Filtro de agencia preferida — rechazar averías sin agencia definida
                        val agenciaNorm = normalizeAveriaText(av.nombreAgencia ?: av.agencia)
                        when {
                            preferredNorm.isNotEmpty() ->
                                agenciaNorm.isNotBlank() &&
                                    preferredNorm.any { pref ->
                                        agenciaNorm.contains(pref) || pref.contains(agenciaNorm)
                                    }
                            fallbackAgency != null ->
                                agenciaNorm.contains(normalizeAveriaText(fallbackAgency))
                            else -> true
                        }
                    }
                    renderMarkers(filtered)
                }
            }
        }
    }

    private fun renderMarkers(averias: List<AveriaEntity>) {
        if (!::googleMap.isInitialized) return
        googleMap.clear()
        markerToAveria.clear()

        averias.forEach { averia ->
            val lat = averia.lat ?: return@forEach
            val lng = averia.lng ?: return@forEach
            if (lat == 0.0 && lng == 0.0) return@forEach

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title("Avería #${averia.caseId}")
                    .snippet(buildSnippet(averia))
                    .icon(markerIcon(averia.estado))
                    .anchor(0.5f, 0.5f)
            )
            if (marker != null) markerToAveria[marker.id] = averia
        }
    }

    private fun buildSnippet(averia: AveriaEntity): String = buildString {
        averia.nombreAgencia?.takeIf { it.isNotBlank() }?.let { append(it) }
        averia.estado.takeIf { it.isNotBlank() }?.let {
            if (isNotBlank()) append(" · ")
            append(it)
        }
    }

    private fun showMarkerCard(averia: AveriaEntity) {
        val estadoLabel = Estado.fromLabel(averia.estado)
        b.tvMarkerTitulo.text = "⚡ Avería #${averia.caseId}"

        // Fecha y hora del evento
        if (averia.fechaInicioMillis > 0) {
            b.tvMarkerFecha.text = "🕐 ${dateTimeFormat.format(Date(averia.fechaInicioMillis))}"
            b.tvMarkerFecha.isVisible = true
        } else {
            b.tvMarkerFecha.isVisible = false
        }

        // Agencia + chip estado
        val agencia = averia.nombreAgencia?.takeIf { it.isNotBlank() } ?: averia.agencia
        b.tvMarkerAgencia.text = agencia?.let { "🏢 $it" } ?: ""
        b.tvMarkerAgencia.isVisible = !agencia.isNullOrBlank()

        val chipBgColor = when (estadoLabel) {
            Estado.PENDIENTE   -> ContextCompat.getColor(requireContext(), R.color.chip_pendiente)
            Estado.ASIGNADA    -> ContextCompat.getColor(requireContext(), R.color.chip_asignada)
            Estado.EN_ATENCION -> ContextCompat.getColor(requireContext(), R.color.chip_en_atencion)
            Estado.RESUELTA    -> ContextCompat.getColor(requireContext(), R.color.chip_resuelta)
            Estado.ANULADA     -> ContextCompat.getColor(requireContext(), R.color.chip_anulada)
        }
        b.chipMarkerEstado.text = averia.estado
        b.chipMarkerEstado.chipBackgroundColor = android.content.res.ColorStateList.valueOf(chipBgColor)
        b.chipMarkerEstado.setTextColor(Color.WHITE)

        // Técnico asignado
        val tecnico = averia.tecnicoAsignadoNombre?.takeIf { it.isNotBlank() }
        b.tvMarkerTecnico.text = tecnico?.let { "👷 $it" } ?: ""
        b.tvMarkerTecnico.isVisible = !tecnico.isNullOrBlank()

        // Atendido por (solo resueltas)
        val atendidoPor = averia.atendidoPorNombre?.takeIf { it.isNotBlank() }
        if (estadoLabel == Estado.RESUELTA && !atendidoPor.isNullOrBlank()) {
            b.tvMarkerAtendidoPor.text = "✅ Atendido por: $atendidoPor"
            b.tvMarkerAtendidoPor.isVisible = true
        } else {
            b.tvMarkerAtendidoPor.isVisible = false
        }

        // Causa
        val causa = averia.causa?.takeIf { it.isNotBlank() }
        b.tvMarkerCausa.text = causa?.let { "⚠️ $it" } ?: ""
        b.tvMarkerCausa.isVisible = !causa.isNullOrBlank()

        // Dirección
        val dir = averia.direccion?.takeIf { it.isNotBlank() }
            ?: averia.localizacion?.takeIf { it.isNotBlank() }
        b.tvMarkerDireccion.text = dir?.let { "📍 $it" } ?: ""
        b.tvMarkerDireccion.isVisible = !dir.isNullOrBlank()

        // NISE
        val nise = averia.nise?.takeIf { it.isNotBlank() }
        b.tvMarkerNise.text = nise?.let { "🔢 NISE: $it" } ?: ""
        b.tvMarkerNise.isVisible = !nise.isNullOrBlank()

        // Observaciones
        val obs = averia.observaciones?.takeIf { it.isNotBlank() }
        b.tvMarkerObservacion.text = obs?.let { "📝 $it" } ?: ""
        b.tvMarkerObservacion.isVisible = !obs.isNullOrBlank()

        b.btnMarkerVerAveria.setOnClickListener {
            AveriaDetalleBottomSheet.newInstance(buildAveriaUi(averia))
                .show(childFragmentManager, "detalle_map_averia")
        }

        b.cardMarkerInfo.animate()
            .alpha(1f).translationY(0f).setDuration(220)
            .withStartAction {
                b.cardMarkerInfo.translationY = 120f
                b.cardMarkerInfo.alpha = 0f
                b.cardMarkerInfo.isVisible = true
            }.start()
    }

    private fun hideMarkerCard() {
        b.cardMarkerInfo.animate()
            .alpha(0f).translationY(120f).setDuration(160)
            .withEndAction { b.cardMarkerInfo.isVisible = false }
            .start()
    }

    private fun buildAveriaUi(entity: AveriaEntity): AveriaUI {
        val materialesDetalle = MaterialesSerializer.fromJson(entity.materialesDetalleJson)
        val materialesResumen = entity.materialesTexto
            ?: MaterialesSerializer.toSummary(materialesDetalle)
        val tecnicosAtendieron = TecnicosSerializer.fromJson(entity.tecnicosAtendieronJson)
        val evidencias = EvidenciasSerializer.fromJson(entity.evidenciasJson)
        return AveriaUI(
            id = entity.caseId,
            descripcion = "Avería #${entity.caseId}",
            fechaMillis = entity.fechaInicioMillis,
            causa = entity.causa?.trim().orEmpty(),
            estado = entity.estado,
            tecnico = entity.tecnicoAsignadoNombre ?: "",
            tecnicoUid = entity.tecnicoAsignadoUid,
            atendidoPor = entity.atendidoPorNombre ?: "",
            atendidoPorUid = entity.atendidoPorUid,
            observaciones = entity.observaciones?.trim().orEmpty(),
            nise = entity.nise ?: "",
            agencia = entity.nombreAgencia ?: (entity.agencia ?: ""),
            region = entity.region ?: "",
            zonaTag = entity.agenciaTag,
            lat = entity.lat ?: 0.0,
            lng = entity.lng ?: 0.0,
            vehiculo = entity.vehiculoAsignado,
            materialesResumen = materialesResumen,
            materialesDetalle = materialesDetalle,
            horaAtencionInicio = entity.atencionHoraInicioMillis,
            horaAtencionFinal = entity.atencionHoraFinalMillis,
            horaLlegada = entity.horaLlegadaMillis,
            kilometrajeInicio = entity.kilometrajeInicio,
            kilometrajeLlegada = entity.kilometrajeLlegada,
            kilometrajeFinal = entity.kilometrajeFinal,
            horaInicio = entity.horaInicioMillis,
            horaFinal = entity.horaFinalMillis,
            cliente = entity.cliente?.trim(),
            localizacion = entity.localizacion?.trim(),
            direccion = entity.direccion?.trim(),
            tecnicosAtendieron = tecnicosAtendieron,
            tipoAfectacion = TipoAfectacion.fromRaw(entity.tipoAfectacion),
            numeroMedidor = entity.numeroMedidor?.trim(),
            medidorCalle = entity.medidorCalle?.trim(),
            medidorPueblo = entity.medidorPueblo?.trim(),
            medidorMetros = entity.medidorMetros?.trim(),
            medidorPoste = entity.medidorPoste?.trim(),
            causaClor = entity.causaClor?.trim(),
            estadoClor = entity.estadoClor?.trim(),
            observacionesClor = entity.observacionesClor?.trim(),
            evidencias = evidencias
        )
    }

    // ─── Marcadores modernos ───────────────────────────────────────────────────

    private fun markerIcon(estado: String): BitmapDescriptor = when (Estado.fromLabel(estado)) {
        Estado.PENDIENTE -> pendienteMarker()
        Estado.RESUELTA  -> resueltoMarker()
        else             -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
    }

    private fun markerIconSelected(estado: String): BitmapDescriptor = when (Estado.fromLabel(estado)) {
        Estado.PENDIENTE -> pendienteMarker(selected = true)
        Estado.RESUELTA  -> resueltoMarker(selected = true)
        else             -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
    }

    /** Círculo rojo con rayo — PENDIENTE (tamaño reducido: 40dp) */
    private fun pendienteMarker(selected: Boolean = false): BitmapDescriptor {
        val dp = resources.displayMetrics.density
        val size = (40 * dp).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - 3 * dp

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.parseColor("#FF005DAA") else Color.parseColor("#80B71C1C")
            style = Paint.Style.STROKE
            strokeWidth = if (selected) 4f * dp else 2.5f * dp
        }.also { canvas.drawCircle(cx, cy, r + 2.5f * dp, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
            style = Paint.Style.FILL
        }.also { canvas.drawCircle(cx, cy, r, it) }

        val s = size * 0.22f
        val bolt = Path().apply {
            moveTo(cx + s * 0.45f, cy - s * 1.05f)
            lineTo(cx - s * 0.65f, cy + s * 0.25f)
            lineTo(cx - s * 0.05f, cy + s * 0.05f)
            lineTo(cx - s * 0.45f, cy + s * 1.05f)
            lineTo(cx + s * 0.65f, cy - s * 0.25f)
            lineTo(cx + s * 0.05f, cy - s * 0.05f)
            close()
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }.also { canvas.drawPath(bolt, it) }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** Círculo verde con check — RESUELTA (tamaño reducido: 40dp) */
    private fun resueltoMarker(selected: Boolean = false): BitmapDescriptor {
        val dp = resources.displayMetrics.density
        val size = (40 * dp).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - 3 * dp

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.parseColor("#FF005DAA") else Color.parseColor("#802E7D32")
            style = Paint.Style.STROKE
            strokeWidth = if (selected) 4f * dp else 2.5f * dp
        }.also { canvas.drawCircle(cx, cy, r + 2.5f * dp, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#43A047")
            style = Paint.Style.FILL
        }.also { canvas.drawCircle(cx, cy, r, it) }

        val s = size * 0.22f
        val check = Path().apply {
            moveTo(cx - s * 0.85f, cy + s * 0.05f)
            lineTo(cx - s * 0.15f, cy + s * 0.9f)
            lineTo(cx + s * 0.85f, cy - s * 0.7f)
        }
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * dp
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }.also { canvas.drawPath(check, it) }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
