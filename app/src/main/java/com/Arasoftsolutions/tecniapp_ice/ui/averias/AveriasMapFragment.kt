package com.Arasoftsolutions.tecniapp_ice.ui.averias

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
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
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentAveriasMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class AveriasMapFragment : Fragment(), OnMapReadyCallback {

    private var _b: FragmentAveriasMapBinding? = null
    private val b get() = _b!!

    private lateinit var googleMap: GoogleMap
    private val markerToAveria = mutableMapOf<String, AveriaEntity>()
    private var selectedMarker: Marker? = null

    private val repo by lazy {
        AveriasRepository(AppDatabase.getInstance(requireContext()))
    }

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

        b.fabBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        b.btnMarkerCerrar.setOnClickListener {
            hideMarkerCard()
        }

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

        googleMap.setOnMarkerClickListener { marker ->
            selectedMarker?.setIcon(markerIcon(markerToAveria[selectedMarker?.id]?.estado ?: ""))
            selectedMarker = marker
            val averia = markerToAveria[marker.id]
            if (averia != null) {
                marker.setIcon(markerIconSelected())
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

    private fun observeAverias() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observe(emptyList(), "", "", "").collectLatest { averias ->
                    renderMarkers(averias)
                }
            }
        }
    }

    private fun renderMarkers(averias: List<AveriaEntity>) {
        if (!::googleMap.isInitialized) return
        googleMap.clear()
        markerToAveria.clear()

        val boundsBuilder = LatLngBounds.builder()
        var validCount = 0

        averias.forEach { averia ->
            val lat = averia.lat ?: return@forEach
            val lng = averia.lng ?: return@forEach
            if (lat == 0.0 && lng == 0.0) return@forEach

            val pos = LatLng(lat, lng)
            val icon = markerIcon(averia.estado)
            val snippet = buildSnippet(averia)

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title("Avería #${averia.caseId}")
                    .snippet(snippet)
                    .icon(icon)
            )
            if (marker != null) {
                markerToAveria[marker.id] = averia
            }
            boundsBuilder.include(pos)
            validCount++
        }

        if (validCount > 0) {
            runCatching {
                val bounds = boundsBuilder.build()
                val padding = resources.getDimensionPixelSize(R.dimen.averia_map_padding)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            }
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

        val agencia = averia.nombreAgencia?.takeIf { it.isNotBlank() } ?: averia.agencia
        b.tvMarkerAgencia.text = agencia?.let { "🏢 $it" } ?: ""
        b.tvMarkerAgencia.isVisible = !agencia.isNullOrBlank()

        val chipBgColor = when (estadoLabel) {
            Estado.PENDIENTE -> ContextCompat.getColor(requireContext(), R.color.chip_pendiente)
            Estado.ASIGNADA -> ContextCompat.getColor(requireContext(), R.color.chip_asignada)
            Estado.EN_ATENCION -> ContextCompat.getColor(requireContext(), R.color.chip_en_atencion)
            Estado.RESUELTA -> ContextCompat.getColor(requireContext(), R.color.chip_resuelta)
            Estado.ANULADA -> ContextCompat.getColor(requireContext(), R.color.chip_anulada)
        }
        b.chipMarkerEstado.text = averia.estado
        b.chipMarkerEstado.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(chipBgColor)
        b.chipMarkerEstado.setTextColor(Color.WHITE)

        val dir = averia.direccion?.takeIf { it.isNotBlank() }
            ?: averia.localizacion?.takeIf { it.isNotBlank() }
        b.tvMarkerDireccion.text = dir?.let { "📍 $it" } ?: ""
        b.tvMarkerDireccion.isVisible = !dir.isNullOrBlank()

        val nise = averia.nise?.takeIf { it.isNotBlank() }
        b.tvMarkerNise.text = nise?.let { "🔢 NISE: $it" } ?: ""
        b.tvMarkerNise.isVisible = !nise.isNullOrBlank()

        val obs = averia.observaciones?.takeIf { it.isNotBlank() }
        b.tvMarkerObservacion.text = obs?.let { "📝 $it" } ?: ""
        b.tvMarkerObservacion.isVisible = !obs.isNullOrBlank()

        b.btnMarkerVerAveria.setOnClickListener {
            val ui = buildAveriaUi(averia)
            AveriaDetalleBottomSheet.newInstance(ui)
                .show(childFragmentManager, "detalle_map_averia")
        }

        b.cardMarkerInfo.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .withStartAction {
                b.cardMarkerInfo.translationY = 100f
                b.cardMarkerInfo.alpha = 0f
                b.cardMarkerInfo.isVisible = true
            }
            .start()
    }

    private fun hideMarkerCard() {
        b.cardMarkerInfo.animate()
            .alpha(0f)
            .translationY(100f)
            .setDuration(150)
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

    /** Icono de marcador según estado de la avería */
    private fun markerIcon(estado: String): BitmapDescriptor {
        val color = when (Estado.fromLabel(estado)) {
            Estado.RESUELTA -> BitmapDescriptorFactory.HUE_GREEN
            Estado.ANULADA -> BitmapDescriptorFactory.HUE_AZURE
            Estado.EN_ATENCION -> BitmapDescriptorFactory.HUE_ORANGE
            Estado.ASIGNADA -> BitmapDescriptorFactory.HUE_YELLOW
            Estado.PENDIENTE -> BitmapDescriptorFactory.HUE_RED
        }
        return BitmapDescriptorFactory.defaultMarker(color)
    }

    /** Marcador seleccionado — destaca con azul y más grande */
    private fun markerIconSelected(): BitmapDescriptor {
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
