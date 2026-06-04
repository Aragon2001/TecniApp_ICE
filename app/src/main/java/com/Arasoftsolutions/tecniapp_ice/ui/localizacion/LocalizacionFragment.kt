package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

// ViewModel en su package correcto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLocalizacionBinding
import com.Arasoftsolutions.tecniapp_ice.ui.averias.AveriaMapLauncher
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.StreetViewPanoramaLocation
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.tasks.CancellationTokenSource
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.max

/**
 * LocalizacionFragment
 *
 * Diseño:
 * - GoogleMap dentro de MapView (ciclo de vida completo)
 * - FusedLocationProviderClient para updates
 * - Activity Result API para permisos (sin APIs deprecadas)
 * - Un ÚNICO listener de "idle" que coordina brújula + autorrotación (Maps permite 1 a la vez)
 * - Rotación con sensor y umbral de cambio (anti-jitter)
 * - UI reactiva via ViewModel (pueblos, calles, localización)
 *
 * Nota: No “escondemos” errores; se loguean con contexto y se muestran toasts útiles para operación en campo.
 */
class LocalizacionFragment : Fragment(), OnMapReadyCallback, SensorEventListener {

    // --- ViewBinding ---
    private var _binding: FragmentLocalizacionBinding? = null
    private val binding get() = _binding!!

    // --- ViewModel (scope del fragment) ---
    private val viewModel: LocalizacionViewModel by viewModels()

    // --- Mapa/Ubicación ---
    private lateinit var mapaVista: MapView
    private var mapaGoogle: GoogleMap? = null        // nullable para evitar isInitialized siempre-true
    private var marcador: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CLAVE_MAPA_VISTA_BUNDLE = "ClaveMapaVistaBundle"
    private val CLAVE_STREET_VIEW_BUNDLE = "ClaveStreetViewBundle"
    private var streetViewPanoramaView: StreetViewPanoramaView? = null
    private var streetViewContainer: FrameLayout? = null
    private var streetViewPanorama: StreetViewPanorama? = null
    private var streetViewHasPanorama = false
    private var streetViewLoading = false
    private var streetViewMode = StreetViewMode.HIDDEN
    private var streetViewBehavior: BottomSheetBehavior<*>? = null
    private var pendingStreetViewLatLng: LatLng? = null
    private var streetViewUnavailableTarget: LatLng? = null
    private var streetViewRequestedTarget: LatLng? = null
    private var streetViewSavedState: Bundle? = null
    private var streetViewCreated = false
    private var streetViewStarted = false
    private var streetViewResumed = false
    private var locationUpdatesActive = false
    private var infoWindowAdapterConfigured = false
    private var mostrarCalles = true
    private var posteMarkerIconDescriptor: BitmapDescriptor? = null
    private val mapLayers = intArrayOf(GoogleMap.MAP_TYPE_NORMAL, GoogleMap.MAP_TYPE_SATELLITE, GoogleMap.MAP_TYPE_HYBRID)
    private var currentMapLayerIndex = 0
    private var pendingStreetUnavailableCheck: Runnable? = null

    private data class StreetMarkerTag(
        val codigoPueblo: Int,
        val codigoCalle: Int,
        val direccion: String
    )

    private enum class StreetViewMode {
        HIDDEN,
        COLLAPSED,
        EXPANDED
    }

    // --- Permisos (Activity Result API) ---
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                enableMyLocationAndStartUpdates()
            } else {
                Toast.makeText(requireContext(), getString(R.string.localizacion_permiso_denegado), Toast.LENGTH_SHORT).show()
            }
        }

    // --- Sensores y rotación ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var autoRotatePreference = true
    private var runtimeAutoRotateEnabled = false  // autorrotación controlada por brújula/sensor
    private var isCompassTouched = false    // true cuando la orientación vuelve ~Norte (bearing≈0)
    private var userIsInteracting = false   // true mientras el usuario mueve la cámara
    private var userRotatedDuringGesture = false
    private var gestureStartBearing: Float? = null

    // Brújula
    private val BEARING_THRESHOLD_DEG = 2f
    private val BEARING_MIN_INTERVAL_MS = 350L
    private val BEARING_SMOOTHING = 0.18f
    private var lastCompassBearing: Float? = null
    private var lastBearingUpdateAt = 0L

    // --- Handler para pequeñas demoras (re-activar autorrotación tras gestos, etc.) ---
    private val handler = Handler(Looper.getMainLooper())

    // --- Ubicación del usuario ---
    private var followLocationEnabled = true
    private var hasCenteredOnUser = false
    private var lastLocationReceivedAt = 0L
    private var singleLocationToken: CancellationTokenSource? = null
    private val LOCATION_STALE_THRESHOLD_MS = 15_000L
    private var gpsActivarPromptShown = false

    // --- Requests de ubicación ---
    private val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        750 // updates frecuentes para seguimiento en tiempo real
    ).apply {
        setMinUpdateIntervalMillis(500)
        setMaxUpdateDelayMillis(2_000)
        setMinUpdateDistanceMeters(1f)
        setWaitForAccurateLocation(false)
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            manejarUbicacionUsuario(location)
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            if (!locationAvailability.isLocationAvailable) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastLocationReceivedAt >= LOCATION_STALE_THRESHOLD_MS) {
                    mostrarIndicadorGpsNoDisponible()
                }
            } else {
                ocultarIndicadorGps()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ciclo de vida / Set up
    // ---------------------------------------------------------------------------------------------
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalizacionBinding.inflate(inflater, container, false)

        // Ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        autoRotatePreference = true
        runtimeAutoRotateEnabled = false

        // Sensores
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR).also {
            if (it == null) Log.e("Localizacion", "Sensor de rotación no disponible")
        }

        configurarObservers()
        configurarStreetViewSheet()
        configurarControles()
        streetViewSavedState = savedInstanceState?.getBundle(CLAVE_STREET_VIEW_BUNDLE)
        inicializarMapaVista(savedInstanceState)

        // Datos iniciales
        viewModel.prepararDatos()

        return binding.root
    }

    private fun configurarObservers() {
        // Pueblos
        viewModel.pueblos.observe(viewLifecycleOwner) { pueblos ->
            binding.spinnerPueblos.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                pueblos
            )
        }

        // Calles dependientes del pueblo
        viewModel.calles.observe(viewLifecycleOwner) { calles ->
            limpiarCamposDeTexto()
            binding.spinnerCalles.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                calles
            )
            binding.spinnerCalles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val calleSeleccionada = parent?.getItemAtPosition(position)?.toString().orEmpty()
                    if (calleSeleccionada == getString(R.string.localizacion_select_calle)) return

                    val codigoCalle = parseCodigo(calleSeleccionada)
                    val direccionCalle = calleSeleccionada.substringAfter(" - ", "").trim()
                    val puebloSel = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
                    if (puebloSel == getString(R.string.localizacion_select_pueblo)) return
                    val codigoPueblo = parseCodigo(puebloSel)

                    if (codigoCalle == null || codigoPueblo == null || direccionCalle.isBlank()) {
                        Log.e("Localizacion", "Error parseando calle seleccionada: calle=$calleSeleccionada pueblo=$puebloSel")
                        return
                    }

                    viewModel.cargarLocalizacionParaCalle(codigoCalle, codigoPueblo, direccionCalle)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        viewModel.marcadoresCalles.observe(viewLifecycleOwner) { markers ->
            renderizarMarcadoresCalles(markers)
        }

        // Localización ↦ actualizar mapa + textos
        viewModel.localizacion.observe(viewLifecycleOwner) { loc ->
            if (loc == null) return@observe

            val puebloSel = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
            val codigoPueblo = puebloSel.takeIf { it.contains(" - ") }?.split(" - ")?.getOrNull(0)
            val codigoCalle = loc.calleValor.toString()
            val numeroPoste = loc.delPoste.toString()

            actualizarUbicacionMapa(loc.latitud, loc.longitud, codigoPueblo, codigoCalle, numeroPoste)
            val nuevoTarget = LatLng(loc.latitud, loc.longitud)
            val targetCambio = !pendingStreetViewLatLng.sameCoordinateAs(nuevoTarget)
            pendingStreetViewLatLng = nuevoTarget
            streetViewRequestedTarget = pendingStreetViewLatLng
            if (targetCambio) {
                streetViewUnavailableTarget = null
            }
            val streetViewDisponible = isValidCoordinate(loc.latitud, loc.longitud) &&
                !streetViewUnavailableTarget.sameCoordinateAs(nuevoTarget)
            binding.actionStreetView.isEnabled = streetViewDisponible
            if (!streetViewDisponible) {
                cerrarStreetView()
            }
            actualizarStreetViewSiEstaActivo()

            binding.locationTitle.text = getString(
                R.string.localizacion_overlay_title_poste,
                loc.delPoste,
                loc.direccion.ifBlank { getString(R.string.localizacion_overlay_title) }
            )
            binding.locationCoordinates.text = getString(
                R.string.localizacion_coordenadas_format,
                loc.latitud,
                loc.longitud
            )
            binding.actionStreetView.isEnabled = streetViewDisponible
            binding.actionCopy.isEnabled = true
            binding.actionCenter.isEnabled = true
            binding.actionNavigate.isEnabled = true
            binding.actionShare.isEnabled = true
        }

        // Estado (progress + errores)
        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is LocalizacionViewModel.Estado.Cargando -> binding.progressBar.visibility = View.VISIBLE
                is LocalizacionViewModel.Estado.Exito   -> binding.progressBar.visibility = View.GONE
                is LocalizacionViewModel.Estado.Error   -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_SHORT).show()
                }
                else -> binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.streetViewState.observe(viewLifecycleOwner) { estado ->
            renderizarStreetViewEstado(estado)
        }

        // Cambio de pueblo ↦ solicitar calles
        binding.spinnerPueblos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val puebloSeleccionado = parent?.getItemAtPosition(position)?.toString().orEmpty()
                if (puebloSeleccionado == getString(R.string.localizacion_select_pueblo)) {
                    actualizarBotonCalles()
                    return
                }
                val codigoPueblo = parseCodigo(puebloSeleccionado)
                if (codigoPueblo == null) {
                    Log.e("Localizacion", "Error parseando pueblo seleccionado: $puebloSeleccionado")
                    return
                }
                limpiarCamposDeTexto()
                viewModel.cargarCallesParaPueblo(codigoPueblo)
                actualizarBotonCalles()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun parseCodigo(item: String): Int? =
        item.substringBefore(" - ", item).trim().toIntOrNull()

    private fun configurarControles() {
        binding.actionNavigate.setOnClickListener { mostrarOpcionesDeNavegacion() }
        binding.actionStreetView.setOnClickListener { abrirStreetView() }
        binding.actionCenter.setOnClickListener { centrarMapaEnLocalizacion() }
        binding.actionCopy.setOnClickListener { copiarCoordenadas() }
        binding.actionShare.setOnClickListener { compartirLocalizacion() }
        binding.actionToggleStreets.setOnClickListener { alternarVisibilidadCalles() }
        binding.actionMapLayer.setOnClickListener { alternarCapaMapa() }
        binding.streetViewActionSurfaceMode.setOnClickListener { irAVistaAereaDesdeStreetView() }
        binding.streetViewActionExpand.setOnClickListener { expandirStreetView() }
        binding.streetViewActionMinimize.setOnClickListener { minimizarStreetView() }
        binding.streetViewActionClose.setOnClickListener { cerrarStreetView() }

        // FABs de control del mapa
        binding.fabRelocate.setOnClickListener {
            followLocationEnabled = true
            hasCenteredOnUser = false
            gpsActivarPromptShown = false  // permitir prompt si GPS sigue apagado
            solicitarUbicacionActual(force = true)
        }
        binding.fabZoomIn.setOnClickListener {
            mapaGoogle?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        binding.fabZoomOut.setOnClickListener {
            mapaGoogle?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        // Botón de reintentar GPS desde el indicador discreto
        binding.gpsRetryButton.setOnClickListener {
            if (!isLocationEnabled()) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                ocultarIndicadorGps()
                solicitarUbicacionActual(force = true)
            }
        }
        binding.actionStreetView.isEnabled = false
        binding.actionCopy.isEnabled = false
        binding.actionCenter.isEnabled = false
        binding.actionNavigate.isEnabled = false
        binding.actionShare.isEnabled = false
        actualizarBotonCalles()
        actualizarStreetViewUi()
    }

    private fun configurarStreetViewSheet() {
        val behavior = BottomSheetBehavior.from(binding.streetViewSheet).also { streetViewBehavior = it }
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        streetViewMode = StreetViewMode.HIDDEN
        behavior.isHideable = true
        behavior.skipCollapsed = false
        behavior.isDraggable = false
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                streetViewMode = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> StreetViewMode.EXPANDED
                    BottomSheetBehavior.STATE_COLLAPSED -> StreetViewMode.COLLAPSED
                    BottomSheetBehavior.STATE_HIDDEN -> StreetViewMode.HIDDEN
                    else -> streetViewMode
                }
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    releaseStreetViewResources()
                    viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.CLOSED)
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_EXPANDED) {
                    ensureStreetViewInflatedAndInitialized()
                    actualizarStreetViewSiEstaActivo()
                    actualizarStreetViewEstadoDesdeSheet()
                }
                actualizarStreetViewUi()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Mantén interacción suave mientras se desliza el bottom sheet.
            }
        })
    }

    private fun abrirStreetView() {

        val target = getStreetViewTargetOrNull()
        if (target == null || streetViewUnavailableTarget.sameCoordinateAs(target)) {
            binding.actionStreetView.isEnabled = false
            Snackbar.make(
                binding.root,
                getString(R.string.localizacion_street_view_unavailable_toast),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Reducir opacidad del mapa para dejar ver Street View, pero suficiente para ver el marcador
        binding.mapView.animate()
            .alpha(0.45f)
            .setDuration(250)
            .start()

        pendingStreetViewLatLng = target
        streetViewRequestedTarget = target
        mapaGoogle?.mapType = GoogleMap.MAP_TYPE_NORMAL

        ensureStreetViewInflatedAndInitialized()

        streetViewMode = StreetViewMode.EXPANDED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

        binding.streetViewSheet.alpha = 0f
        binding.streetViewSheet.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }


    private fun expandirStreetView() {
        ensureStreetViewInflatedAndInitialized()
        streetViewMode = StreetViewMode.EXPANDED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }

    private fun minimizarStreetView() {
        ensureStreetViewInflatedAndInitialized()
        streetViewMode = StreetViewMode.COLLAPSED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }

    private fun cerrarStreetView() {

    streetViewMode = StreetViewMode.HIDDEN
    streetViewBehavior?.state = BottomSheetBehavior.STATE_HIDDEN

    releaseStreetViewResources()

    viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.CLOSED)
    actualizarStreetViewUi()

    // Restaurar opacidad del mapa
    binding.mapView.animate()
        .alpha(1f)
        .setDuration(250)
        .start()
}




    private fun ensureStreetViewInflatedAndInitialized() {
        val target = getStreetViewTargetOrNull() ?: return

        if (streetViewPanoramaView == null) {
            // OOM prevention: StreetView solo se infla cuando el usuario realmente abre el sheet.
            val inflated = binding.streetViewStub.inflate()
            streetViewContainer = inflated as? FrameLayout
            streetViewPanoramaView = streetViewContainer?.findViewById(R.id.streetViewPanorama)
        }

        val panoramaView = streetViewPanoramaView ?: return
        if (!streetViewCreated) {
            panoramaView.onCreate(streetViewSavedState)
            streetViewSavedState = null
            streetViewCreated = true
            panoramaView.getStreetViewPanoramaAsync { panorama ->
                streetViewPanorama = panorama.apply {
                    setUserNavigationEnabled(true)
                    setPanningGesturesEnabled(true)
                    setZoomGesturesEnabled(true)
                    setStreetNamesEnabled(true)
                    setOnStreetViewPanoramaChangeListener { location: StreetViewPanoramaLocation? ->
                        if (location == null) {
                            streetViewHasPanorama = false
                            pendingStreetUnavailableCheck?.let { handler.removeCallbacks(it) }
                            val unavailableCheck = Runnable {
                                if (!isAdded || streetViewHasPanorama) return@Runnable
                                streetViewLoading = false
                                streetViewUnavailableTarget = streetViewRequestedTarget
                                binding.actionStreetView.isEnabled = false
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.localizacion_street_view_unavailable_toast),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                streetViewMode = StreetViewMode.HIDDEN
                                streetViewBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                                streetViewContainer?.isVisible = false
                                actualizarStreetViewEstadoDesdeSheet()
                                actualizarStreetViewUi()
                            }
                            pendingStreetUnavailableCheck = unavailableCheck
                            handler.postDelayed(unavailableCheck, 700L)
                            return@setOnStreetViewPanoramaChangeListener
                        }

                        pendingStreetUnavailableCheck?.let { handler.removeCallbacks(it) }
                        pendingStreetUnavailableCheck = null
                        streetViewHasPanorama = true
                        streetViewLoading = false
                        streetViewUnavailableTarget = null
                        binding.actionStreetView.isEnabled = true
                        streetViewContainer?.isVisible = true
                        actualizarStreetViewEstadoDesdeSheet()
                        actualizarStreetViewUi()
                    }
                }
               streetViewLoading = true
               panorama.setPosition(target)

               val camera = StreetViewPanoramaCamera.Builder()
                   .zoom(0f)
                   .tilt(0f)
                   .bearing(0f)
                   .build()
               panorama.animateTo(camera, 800)
            }
        }

        panoramaView.isVisible = true
        if (!streetViewStarted) {
            panoramaView.onStart()
            streetViewStarted = true
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && !streetViewResumed) {
            panoramaView.onResume()
            streetViewResumed = true
        }
    }

    private fun releaseStreetViewResources() {
        val panorama = streetViewPanorama
        panorama?.setOnStreetViewPanoramaChangeListener(null)
        panorama?.setOnStreetViewPanoramaCameraChangeListener(null)

        streetViewPanorama = null
        streetViewHasPanorama = false
        streetViewLoading = false
        pendingStreetUnavailableCheck?.let { handler.removeCallbacks(it) }
        pendingStreetUnavailableCheck = null

        val panoramaView = streetViewPanoramaView ?: return
        if (streetViewResumed) {
            panoramaView.onPause()
            streetViewResumed = false
        }
        if (streetViewStarted) {
            panoramaView.onStop()
            streetViewStarted = false
        }
        if (streetViewCreated) {
            panoramaView.onDestroy()
            streetViewCreated = false
        }

        // OOM prevention: destruimos el renderer de Street View al cerrar el sheet,
        // evitando que MapView + StreetView mantengan buffers pesados en paralelo.
        streetViewPanoramaView?.isVisible = false
    }

    private fun actualizarStreetViewEstadoDesdeSheet() {
        val target = getStreetViewTargetOrNull()
        val unavailableForTarget = target != null && streetViewUnavailableTarget.sameCoordinateAs(target)
        val estado = when {
            streetViewMode == StreetViewMode.HIDDEN -> LocalizacionViewModel.StreetViewState.CLOSED
            streetViewLoading -> LocalizacionViewModel.StreetViewState.LOADING
            streetViewHasPanorama && streetViewMode == StreetViewMode.EXPANDED ->
                LocalizacionViewModel.StreetViewState.FULLSCREEN
            streetViewHasPanorama && streetViewMode == StreetViewMode.COLLAPSED ->
                LocalizacionViewModel.StreetViewState.MINIMIZED
            unavailableForTarget -> LocalizacionViewModel.StreetViewState.UNAVAILABLE
            else -> LocalizacionViewModel.StreetViewState.LOADING
        }
        viewModel.actualizarStreetViewEstado(estado)
    }

    private fun renderizarStreetViewEstado(estado: LocalizacionViewModel.StreetViewState) {
        val loading = estado == LocalizacionViewModel.StreetViewState.LOADING
        val unavailable = estado == LocalizacionViewModel.StreetViewState.UNAVAILABLE
        val active = estado == LocalizacionViewModel.StreetViewState.FULLSCREEN ||
            estado == LocalizacionViewModel.StreetViewState.MINIMIZED ||
            estado == LocalizacionViewModel.StreetViewState.LOADING

        binding.streetViewLoadingContainer.isVisible = loading
        binding.streetViewEmptyState.isVisible = unavailable
        streetViewPanoramaView?.isVisible = active && !unavailable

        binding.streetViewActionExpand.isVisible = estado == LocalizacionViewModel.StreetViewState.MINIMIZED
        binding.streetViewActionMinimize.isVisible = estado == LocalizacionViewModel.StreetViewState.FULLSCREEN
        binding.streetViewActionSurfaceMode.isVisible = active && !unavailable
    }

    // Reset de textos y cámara a vista país
    private fun limpiarCamposDeTexto() {
        binding.locationTitle.text = getString(R.string.localizacion_overlay_title)
        binding.locationCoordinates.text = getString(R.string.localizacion_placeholder_coordenadas)
        centrarMapaEnCostaRica()
        hasCenteredOnUser = false
        binding.actionStreetView.isEnabled = false
        binding.actionCopy.isEnabled = false
        binding.actionCenter.isEnabled = false
        binding.actionNavigate.isEnabled = false
        streetViewHasPanorama = false
        streetViewLoading = false
        pendingStreetViewLatLng = null
        cerrarStreetView()
    }

    // ---------------------------------------------------------------------------------------------
    // MapView / GoogleMap
    // ---------------------------------------------------------------------------------------------
    private fun inicializarMapaVista(savedInstanceState: Bundle?) {
        val mapaBundle: Bundle? = savedInstanceState?.getBundle(CLAVE_MAPA_VISTA_BUNDLE)
        mapaVista = binding.mapView
        mapaVista.onCreate(mapaBundle)
        mapaVista.getMapAsync(this)
    }


    override fun onMapReady(map: GoogleMap) {
        mapaGoogle = map

        // Controles de UI
        mapaGoogle?.uiSettings?.apply {
            isZoomControlsEnabled = false        // reemplazado por fabZoomIn/fabZoomOut
            isMyLocationButtonEnabled = false    // reemplazado por fabRelocate
            isMapToolbarEnabled = false
            isZoomGesturesEnabled = true
            isTiltGesturesEnabled = true
            isCompassEnabled = true
            isRotateGesturesEnabled = true
            isScrollGesturesEnabledDuringRotateOrZoom = true
            isScrollGesturesEnabled = true
            isIndoorLevelPickerEnabled = false
        }
        // Empujar la brújula nativa hacia abajo para que no quede tapada por overlayTop
        val topPadPx = (140 * resources.displayMetrics.density).toInt()
        mapaGoogle?.setPadding(0, topPadPx, 0, 0)
        mapaGoogle?.apply {
            currentMapLayerIndex = mapLayers.indexOf(mapType).takeIf { it >= 0 } ?: 0
            // Mapa más liviano: desactivamos capas pesadas para reducir uso de memoria/GPU.
            isTrafficEnabled = false
            isBuildingsEnabled = true
            isIndoorEnabled = false
            setMinZoomPreference(3f)
            setMaxZoomPreference(21f)
            setOnMyLocationButtonClickListener {
                followLocationEnabled = true
                hasCenteredOnUser = false
                solicitarUbicacionActual(force = true)
                true
            }
        }

        configurarInfoWindowPersonalizadoSiHaceFalta()
        configurarInteraccionMarcadores()
        setupCameraListeners()        // mueve/idle centralizado
        verificarPermisosUbicacion()  // si ya hay permiso, activa myLocation + updates
        renderizarMarcadoresCalles(viewModel.marcadoresCalles.value.orEmpty())
        centrarMapaEnCostaRica()
    }

    // Listener único para coordinar gestos (move started), movimiento (move) y fin (idle)
    private fun setupCameraListeners() {
        val map = mapaGoogle ?: return

        // 1) Inicio de movimiento (gesto del usuario)
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                userIsInteracting = true
                followLocationEnabled = false
                actualizarAutorrotacionRuntime(false)
                userRotatedDuringGesture = false
                lastCompassBearing = mapaGoogle?.cameraPosition?.bearing?.let { normalizeBearing(it) }
                lastBearingUpdateAt = SystemClock.elapsedRealtime()
                gestureStartBearing = lastCompassBearing
                Log.d("Localizacion", "Gesto detectado → autorrotación OFF")
            }
        }

        // 2) Movimiento continuo (señal para activar autorrotación si giran el mapa explícitamente)
        map.setOnCameraMoveListener {
            val bearing = mapaGoogle?.cameraPosition?.bearing ?: return@setOnCameraMoveListener
            // Si el usuario realmente giró el mapa (bearing cambia), permitimos autorrotación posterior
            if (userIsInteracting) {
                gestureStartBearing?.let { start ->
                    if (abs(deltaBearing(start, bearing)) > 1) {
                        userRotatedDuringGesture = true
                    }
                }
            }
            // Si la orientación es ~Norte durante el movimiento, inferimos toque de brújula
            if (abs(bearing) < 1) {
                actualizarAutorrotacionRuntime(false)
                isCompassTouched = true
            }
        }

        // 3) Fin de movimiento (IDLE ÚNICO)
        map.setOnCameraIdleListener {
            val bearing = mapaGoogle?.cameraPosition?.bearing ?: 0f

            // Si quedó en ~Norte y veníamos “tocando brújula”, mantener autorrotación OFF
            if (abs(bearing) < 1 && isCompassTouched) {
                actualizarAutorrotacionRuntime(false)
                isCompassTouched = false
                Log.d("Localizacion", "Idle ~Norte → autorrotación OFF por brújula")
            }

            if (userIsInteracting) {
                userIsInteracting = false
                actualizarAutorrotacionRuntime(userRotatedDuringGesture && !isCompassTouched)
                userRotatedDuringGesture = false
            }

            lastCompassBearing = normalizeBearing(bearing)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Permisos / Ubicación
    // ---------------------------------------------------------------------------------------------
    private fun verificarPermisosUbicacion() {
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            pedirActivarUbicacionSiOcupa()
            enableMyLocationAndStartUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocationAndStartUpdates() {
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        mapaGoogle?.isMyLocationEnabled = true
        if (!locationUpdatesActive) {
            lastLocationReceivedAt = SystemClock.elapsedRealtime()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            locationUpdatesActive = true
        }
        if (!followLocationEnabled) {
            hasCenteredOnUser = false
        }
        solicitarUbicacionActual(force = true)
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesActive = false
        }
        singleLocationToken?.cancel()
        singleLocationToken = null
    }

    // ---------------------------------------------------------------------------------------------
    // Cámara / Marcadores
    // ---------------------------------------------------------------------------------------------
    private fun centrarMapaEnCostaRica() {
        val cr = LatLng(9.7489, -83.7534)
        mapaGoogle?.moveCamera(CameraUpdateFactory.newLatLngZoom(cr, 7f))
        lastCompassBearing = null
    }

    private fun actualizarUbicacionMapa(
        latitud: Double,
        longitud: Double,
        codigoPueblo: String?,
        numeroCalle: String?,
        numeroPoste: String?
    ) {
        followLocationEnabled = false

        val map = mapaGoogle ?: return
        val ubicacion = LatLng(latitud, longitud)
        val posteTitulo = numeroPoste
            ?.toIntOrNull()
            ?.let { getString(R.string.localizacion_marker_poste_unico, it) }
            ?: getString(R.string.localizacion_marker_title)

        // Formato con ceros a la izquierda: PPPP-CCC-XXX-00
        val snippetInfo = buildString {
            append((codigoPueblo ?: "0").padStart(4, '0'))
            append("-")
            append((numeroCalle ?: "0").padStart(3, '0'))
            append("-")
            append((numeroPoste ?: "0").padStart(3, '0'))
            append("-00")
        }

      if (marcador == null) {
    marcador = map.addMarker(
        MarkerOptions()
            .position(ubicacion)
            .icon(getPosteMarkerIconDescriptor())
            .title(posteTitulo)
            .snippet(snippetInfo)
    )
    marcador?.showInfoWindow()   // ← ESTA LÍNEA FALTABA
} else {
    marcador?.apply {
        position = ubicacion
        title = posteTitulo
        snippet = snippetInfo
        hideInfoWindow()
        showInfoWindow()
    }
}


        val cameraPosition = CameraPosition.Builder()
            .target(ubicacion)
            .zoom(17f)
            .bearing(map.cameraPosition.bearing)
            .tilt(45f)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600, null)
        lastCompassBearing = normalizeBearing(cameraPosition.bearing)
        lastBearingUpdateAt = SystemClock.elapsedRealtime()
    }

    private fun normalizeBearing(raw: Float): Float {
        var value = raw
        while (value < 0f) value += 360f
        while (value >= 360f) value -= 360f
        return value
    }

    private fun deltaBearing(from: Float, to: Float): Float {
        val diff = (to - from + 540f) % 360f - 180f
        return diff
    }

    private fun smoothBearing(previous: Float, target: Float): Float {
        val delta = deltaBearing(previous, target)
        val adjusted = previous + delta * BEARING_SMOOTHING
        return normalizeBearing(adjusted)
    }

    private fun configurarInfoWindowPersonalizadoSiHaceFalta() {
        if (infoWindowAdapterConfigured) return
        mapaGoogle?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null
            override fun getInfoContents(marker: Marker): View {
                val v = layoutInflater.inflate(R.layout.custom_info_window, null)
                v.findViewById<TextView>(R.id.titulo).text = marker.title
                v.findViewById<TextView>(R.id.snippet).text = marker.snippet
                return v
            }
        })
        infoWindowAdapterConfigured = true
    }

    private fun irAVistaAereaDesdeStreetView() {
        val loc = viewModel.localizacion.value
        if (loc == null) {
            Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_ubicacion_mapa), Snackbar.LENGTH_SHORT).show()
            return
        }
        cerrarStreetView()
        mapaGoogle?.mapType = GoogleMap.MAP_TYPE_HYBRID
        centrarMapaEnLocalizacion()
    }

    private fun configurarInteraccionMarcadores() {
        mapaGoogle?.setOnMarkerClickListener { marker ->
            val data = marker.tag as? StreetMarkerTag ?: return@setOnMarkerClickListener false
            seleccionarCalleDesdeMapa(data)
            false
        }
    }

    private fun renderizarMarcadoresCalles(markers: List<LocalizacionViewModel.MarcadorCalle>) {
        val map = mapaGoogle ?: return
        map.clear()
        marcador = null

        if (mostrarCalles) {
            markers.take(250).forEach { marker ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(marker.latitud, marker.longitud))
                    .icon(getPosteMarkerIconDescriptor())
                    .title(getString(R.string.localizacion_marker_poste_unico, marker.delPoste))
                    .snippet(marker.snippet)
            )?.tag = StreetMarkerTag(
                codigoPueblo = marker.codigoPueblo,
                codigoCalle = marker.codigoCalle,
                direccion = marker.direccion
            )
            }
        }

        val loc = viewModel.localizacion.value ?: return
        val codigoPueblo = parseCodigo(binding.spinnerPueblos.selectedItem?.toString().orEmpty())?.toString()
        actualizarUbicacionMapa(
            latitud = loc.latitud,
            longitud = loc.longitud,
            codigoPueblo = codigoPueblo,
            numeroCalle = loc.calleValor.toString(),
            numeroPoste = loc.delPoste.toString()
        )
    }

    private fun seleccionarCalleDesdeMapa(marker: StreetMarkerTag) {
        val puebloItem = buscarItemSpinner(binding.spinnerPueblos, marker.codigoPueblo)
        if (puebloItem == null) {
            Snackbar.make(binding.root, getString(R.string.localizacion_calles_toast_sin_pueblo), Snackbar.LENGTH_SHORT).show()
            return
        }

        val puebloActual = parseCodigo(binding.spinnerPueblos.selectedItem?.toString().orEmpty())
        val cambiarPueblo = puebloActual != marker.codigoPueblo
        if (cambiarPueblo) {
            binding.spinnerPueblos.setSelection(puebloItem)
        }

        binding.spinnerCalles.postDelayed({
            val calleItem = buscarItemSpinner(binding.spinnerCalles, marker.codigoCalle)
            if (calleItem != null) {
                binding.spinnerCalles.setSelection(calleItem)
            }
            val codigoPueblo = parseCodigo(binding.spinnerPueblos.selectedItem?.toString().orEmpty()) ?: marker.codigoPueblo
            viewModel.cargarLocalizacionParaCalle(marker.codigoCalle, codigoPueblo, marker.direccion)
        }, if (cambiarPueblo) 200L else 0L)
    }

    private fun buscarItemSpinner(spinner: android.widget.Spinner, codigo: Int): Int? {
        val adapter = spinner.adapter ?: return null
        for (index in 0 until adapter.count) {
            val item = adapter.getItem(index)?.toString().orEmpty()
            if (parseCodigo(item) == codigo) return index
        }
        return null
    }

    private fun alternarCapaMapa() {
        val map = mapaGoogle ?: return
        currentMapLayerIndex = (currentMapLayerIndex + 1) % mapLayers.size
        val nuevoTipo = mapLayers[currentMapLayerIndex]
        map.mapType = nuevoTipo
        val descripcion = when (nuevoTipo) {
            GoogleMap.MAP_TYPE_SATELLITE -> getString(R.string.localizacion_map_layer_satellite)
            GoogleMap.MAP_TYPE_HYBRID -> getString(R.string.localizacion_map_layer_hybrid)
            else -> getString(R.string.localizacion_map_layer_normal)
        }
        binding.actionMapLayer.contentDescription = descripcion
        Snackbar.make(binding.root, descripcion, Snackbar.LENGTH_SHORT).show()
    }

    private fun alternarVisibilidadCalles() {
        if (!hayPuebloSeleccionado()) return
        mostrarCalles = !mostrarCalles
        actualizarBotonCalles()
        renderizarMarcadoresCalles(viewModel.marcadoresCalles.value.orEmpty())
    }

    private fun actualizarBotonCalles() {
        val habilitado = hayPuebloSeleccionado()
        val icon = if (mostrarCalles) R.drawable.disabled_visible_24px else R.drawable.ic_visibility_off
        val legend = if (mostrarCalles) {
            getString(R.string.localizacion_ocultar_calles)
        } else {
            getString(R.string.localizacion_mostrar_calles)
        }
        binding.actionToggleStreets.isEnabled = habilitado
        binding.actionToggleStreets.setIconResource(icon)
        binding.actionToggleStreets.contentDescription = legend
    }

    private fun hayPuebloSeleccionado(): Boolean {
        val seleccionado = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
        val codigo = parseCodigo(seleccionado)
        return codigo != null && seleccionado != getString(R.string.localizacion_select_pueblo)
    }

    private fun isValidCoordinate(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        return lat != 0.0 || lng != 0.0
    }

    private fun getStreetViewTargetOrNull(): LatLng? {
        val latLng = pendingStreetViewLatLng
        if (isValidCoordinate(latLng?.latitude, latLng?.longitude)) return latLng

        val loc = viewModel.localizacion.value ?: return null
        if (!isValidCoordinate(loc.latitud, loc.longitud)) return null
        return LatLng(loc.latitud, loc.longitud)
    }

    private fun LatLng?.sameCoordinateAs(other: LatLng): Boolean {
        val current = this ?: return false
        return current.latitude == other.latitude && current.longitude == other.longitude
    }

    private fun getPosteMarkerIconDescriptor(): BitmapDescriptor {
    posteMarkerIconDescriptor?.let { return it }

    return try {
        val drawable = requireContext().getDrawable(R.drawable.poste)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val density = resources.displayMetrics.density
        val sizeDp = 50f // 👈 Cambiá esto si querés más pequeño o más grande
        val sizePx = (sizeDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        posteMarkerIconDescriptor = descriptor
        descriptor

    } catch (e: Exception) {
        Log.e("Localizacion", "Error creando icono poste", e)
        BitmapDescriptorFactory.defaultMarker()
    }
}

    private fun buildShareMessage(): String? {
    val loc = viewModel.localizacion.value ?: return null
    val lat = loc.latitud
    val lng = loc.longitud

    val puebloRaw = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
    val calleRaw = binding.spinnerCalles.selectedItem?.toString().orEmpty()

    val codigoPueblo = parseCodigo(puebloRaw)?.toString()?.padStart(4, '0') ?: "0000"
    val nombrePueblo = puebloRaw.substringAfter(" - ", puebloRaw)

    val codigoCalle = parseCodigo(calleRaw)?.toString()?.padStart(3, '0') ?: "000"
    val nombreCalle = calleRaw.substringAfter(" - ", calleRaw)

    val posteDesde = loc.delPoste.toString().padStart(3, '0')
    val posteHasta = loc.alPoste.takeIf { it != 0 && it != loc.delPoste }
        ?.toString()
        ?.padStart(3, '0')

    val codigoCompleto = "$codigoPueblo-$codigoCalle-$posteDesde-00"

    val mapsUrl = "https://maps.google.com/?q=$lat,$lng"

    return buildString {
        appendLine("📍 LOCALIZACIÓN ICE")
        appendLine("━━━━━━━━━━━━━━━━━━")
        appendLine("🏘 Pueblo: $nombrePueblo")
        appendLine("🛣 Calle: $nombreCalle")

        appendLine(
            if (posteHasta != null)
                "🪵 Postes: $posteDesde ➝ $posteHasta"
            else
                "🪵 Poste: $posteDesde"
        )

        appendLine("🔢 Código: $codigoCompleto")
        appendLine()
        appendLine("🌎 Coordenadas:")
        appendLine("   $lat , $lng")
        appendLine()
        appendLine("🔗 Google Maps:")
        appendLine(mapsUrl)
    }
}



    private fun compartirLocalizacion() {
    val message = buildShareMessage()
    if (message == null) {
        Snackbar.make(binding.root, getString(R.string.localizacion_toast_no_compartir), Snackbar.LENGTH_SHORT).show()
        return
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }

    startActivity(Intent.createChooser(shareIntent, "Compartir localización"))
}


    private fun mostrarOpcionesDeNavegacion() {
        val loc = viewModel.localizacion.value
        val lat = loc?.latitud
        val lng = loc?.longitud

        if (lat == null || lng == null) {
            Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_ubicacion_mapa), Snackbar.LENGTH_SHORT).show()
            return
        }

        AveriaMapLauncher.show(
            context = requireContext(),
            lat = lat,
            lng = lng,
            label = ""// o null si no tenés nombre
        ) {
            Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_apps_mapa), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun centrarMapaEnLocalizacion() {
        val loc = viewModel.localizacion.value
        val lat = loc?.latitud
        val lng = loc?.longitud
        if (lat == null || lng == null) {
            Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_ubicacion_mapa), Snackbar.LENGTH_SHORT).show()
            return
        }
        val ubicacion = LatLng(lat, lng)
        val cameraPosition = CameraPosition.Builder()
            .target(ubicacion)
            .zoom(17f)
            .bearing(mapaGoogle?.cameraPosition?.bearing ?: 0f)
            .tilt(45f)
            .build()
        mapaGoogle?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

   private fun copiarCoordenadas() {
    val message = buildShareMessage()
    if (message == null) {
        Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_ubicacion_mapa), Snackbar.LENGTH_SHORT).show()
        return
    }

    val clipboard = requireContext()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText("Localizacion ICE", message)
    )

    Snackbar.make(binding.root, "Información copiada correctamente", Snackbar.LENGTH_SHORT).show()
}


    // ---------------------------------------------------------------------------------------------
    // Sensores / Autorrotación
    // ---------------------------------------------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent?) {
        val map = mapaGoogle ?: return
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (!debeAutorrotar() || isCompassTouched || userIsInteracting) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rawBearing = toDegrees(orientation[0].toDouble()).toFloat()
        val normalizedBearing = normalizeBearing(rawBearing)
        val smoothed = lastCompassBearing?.let { smoothBearing(it, normalizedBearing) } ?: normalizedBearing
        val currentBearing = normalizeBearing(map.cameraPosition.bearing)
        val delta = deltaBearing(currentBearing, smoothed)
        val now = SystemClock.elapsedRealtime()

        if (abs(delta) > BEARING_THRESHOLD_DEG && now - lastBearingUpdateAt > BEARING_MIN_INTERVAL_MS) {
            lastCompassBearing = smoothed
            lastBearingUpdateAt = now
            val pos = CameraPosition.Builder(map.cameraPosition)
                .bearing(smoothed)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Log de diagnóstico; útil para soporte si el sensor se degrada
        when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> Log.w("Sensor", "Sin contacto con el sensor")
            SensorManager.SENSOR_STATUS_UNRELIABLE -> Log.w("Sensor", "Precisión no confiable")
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Log.i("Sensor", "Precisión baja")
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Log.i("Sensor", "Precisión media")
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Log.i("Sensor", "Precisión alta")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ciclo de vida MapView/Sensores
    // ---------------------------------------------------------------------------------------------
    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
        if (streetViewCreated && !streetViewStarted) {
            streetViewPanoramaView?.onStart()
            streetViewStarted = true
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (streetViewCreated && streetViewMode != StreetViewMode.HIDDEN && !streetViewResumed) {
            streetViewPanoramaView?.onResume()
            streetViewResumed = true
        }
        rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if ((fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) && !locationUpdatesActive) {
            enableMyLocationAndStartUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (streetViewResumed) {
            streetViewPanoramaView?.onPause()
            streetViewResumed = false
        }
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        binding.mapView.onStop()
        if (streetViewStarted) {
            streetViewPanoramaView?.onStop()
            streetViewStarted = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        mapaGoogle?.setOnCameraMoveStartedListener(null)
        mapaGoogle?.setOnCameraMoveListener(null)
        mapaGoogle?.setOnCameraIdleListener(null)
        mapaGoogle?.setOnMyLocationButtonClickListener(null)
        mapaGoogle?.setOnMapClickListener(null)
        mapaGoogle?.setInfoWindowAdapter(null)
        infoWindowAdapterConfigured = false
        binding.mapView.onDestroy()
        releaseStreetViewResources()
        handler.removeCallbacksAndMessages(null)
        singleLocationToken?.cancel()
        singleLocationToken = null
        streetViewPanorama = null
        posteMarkerIconDescriptor = null
        _binding = null
        mapaGoogle = null
        marcador = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
        streetViewPanoramaView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapaBundle = outState.getBundle(CLAVE_MAPA_VISTA_BUNDLE) ?: Bundle()
        binding.mapView.onSaveInstanceState(mapaBundle)
        outState.putBundle(CLAVE_MAPA_VISTA_BUNDLE, mapaBundle)
        if (streetViewCreated) {
            val streetBundle = outState.getBundle(CLAVE_STREET_VIEW_BUNDLE) ?: Bundle()
            streetViewPanoramaView?.onSaveInstanceState(streetBundle)
            outState.putBundle(CLAVE_STREET_VIEW_BUNDLE, streetBundle)
        }
    }

    private fun actualizarStreetViewSiEstaActivo() {
        if (streetViewMode == StreetViewMode.HIDDEN || !streetViewCreated) return
        val panorama = streetViewPanorama ?: return
        val target = getStreetViewTargetOrNull() ?: return
        streetViewLoading = true
        viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.LOADING)
        actualizarStreetViewUi()
        panorama.setPosition(target)
    }

    private fun actualizarStreetViewUi() {
        val state = viewModel.streetViewState.value ?: LocalizacionViewModel.StreetViewState.CLOSED
        renderizarStreetViewEstado(state)
    }

    private fun actualizarAutorrotacionRuntime(enabled: Boolean) {
        runtimeAutoRotateEnabled = enabled && autoRotatePreference
    }

    private fun debeAutorrotar(): Boolean = autoRotatePreference && runtimeAutoRotateEnabled

    private fun isLocationEnabled(): Boolean {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun pedirActivarUbicacionSiOcupa() {
        if (!isLocationEnabled() && !gpsActivarPromptShown) {
            gpsActivarPromptShown = true
            Snackbar.make(
                binding.root,
                "Ubicación desactivada. Activá GPS para centrar.",
                Snackbar.LENGTH_LONG
            ).setAction("Activar") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.show()
        }
    }

    private fun solicitarUbicacionActual(force: Boolean = false) {
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        pedirActivarUbicacionSiOcupa()

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        manejarUbicacionUsuario(location)
                    } else if (force) {
                        solicitarUbicacionPrecisa()
                    }
                }
                .addOnFailureListener { throwable ->
                    Log.e("Localizacion", "Error obteniendo lastLocation", throwable)
                    if (force) solicitarUbicacionPrecisa()
                }
        } catch (sec: SecurityException) {
            Log.e("Localizacion", "Permiso de ubicación perdido", sec)
        }
    }

    private fun solicitarUbicacionPrecisa() {
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        singleLocationToken?.cancel()
        val tokenSource = CancellationTokenSource().also { singleLocationToken = it }
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) manejarUbicacionUsuario(location)
                }
                .addOnFailureListener { throwable ->
                    Log.e("Localizacion", "Error en getCurrentLocation", throwable)
                }
        } catch (sec: SecurityException) {
            Log.e("Localizacion", "Permiso de ubicación perdido durante getCurrentLocation", sec)
        }
    }

    private fun manejarUbicacionUsuario(location: Location) {
        lastLocationReceivedAt = SystemClock.elapsedRealtime()
        ocultarIndicadorGps()
        if (followLocationEnabled && !userIsInteracting) {
            moverCamaraAUbicacion(location, animate = hasCenteredOnUser)
            hasCenteredOnUser = true
        }
    }

    private fun moverCamaraAUbicacion(location: Location, animate: Boolean) {
        val map = mapaGoogle ?: return
        val latLng = LatLng(location.latitude, location.longitude)
        val currentPosition = map.cameraPosition
        val zoom = max(currentPosition.zoom, 17f)

        // Usar el bearing del GPS cuando el dispositivo está en movimiento (> 0.5 m/s ≈ 1.8 km/h)
        val bearing = if (location.hasBearing() && location.speed > 0.5f) {
            normalizeBearing(location.bearing)
        } else {
            currentPosition.bearing
        }

        val position = CameraPosition.Builder()
            .target(latLng)
            .zoom(zoom)
            .bearing(bearing)
            .tilt(currentPosition.tilt)
            .build()

        if (animate) {
            // Duración = intervalo de actualización de ubicación → movimiento continuo y fluido
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 750, null)
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }
        lastCompassBearing = normalizeBearing(bearing)
    }

    private fun mostrarIndicadorGpsNoDisponible() {
        _binding?.gpsStatusRow?.visibility = View.VISIBLE
    }

    private fun ocultarIndicadorGps() {
        _binding?.gpsStatusRow?.isVisible = false
    }
}
