package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

// ViewModel en su package correcto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import com.google.android.gms.maps.model.StreetViewSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
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
    private var streetViewPanorama: StreetViewPanorama? = null
    private var streetViewHasPanorama = false
    private var streetViewMode = StreetViewMode.HIDDEN
    private var streetViewBehavior: BottomSheetBehavior<*>? = null

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
    private var lastLocationErrorAt = 0L
    private var singleLocationToken: CancellationTokenSource? = null
    private val LOCATION_ERROR_TOAST_INTERVAL_MS = 10_000L
    private val LOCATION_STALE_THRESHOLD_MS = 5_000L

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
            val location: Location? = locationResult.lastLocation
            if (location == null) {
                mostrarToastUbicacionSiNecesario(getString(R.string.localizacion_toast_sin_ubicacion))
                return
            }
            manejarUbicacionUsuario(location)
            // Si quisieras seguir al usuario, podrías mover la cámara aquí;
            // se deja sin mover para no interferir con el poste seleccionado.
            // val yo = LatLng(location.latitude, location.longitude)
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            if (!locationAvailability.isLocationAvailable) {
                val now = SystemClock.elapsedRealtime()
                val isStale = now - lastLocationReceivedAt >= LOCATION_STALE_THRESHOLD_MS
                if (isStale) {
                    mostrarToastUbicacionSiNecesario(getString(R.string.localizacion_toast_sin_disponibilidad))
                }
            } else {
                lastLocationErrorAt = 0L
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
        inicializarMapaVista(savedInstanceState)
        inicializarStreetView(savedInstanceState)

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

                    try {
                        val partes = calleSeleccionada.split(" - ")
                        val codigoCalle = partes[0].toInt()
                        val direccionCalle = partes[1]
                        val puebloSel = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
                        if (puebloSel == getString(R.string.localizacion_select_pueblo)) return
                        val codigoPueblo = puebloSel.split(" - ")[0].toInt()

                        viewModel.cargarLocalizacionParaCalle(codigoCalle, codigoPueblo, direccionCalle)
                    } catch (e: Exception) {
                        Log.e("Localizacion", "Error parseando calle seleccionada: ${e.message}", e)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Localización ↦ actualizar mapa + textos
        viewModel.localizacion.observe(viewLifecycleOwner) { loc ->
            if (loc == null) return@observe

            val puebloSel = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
            val codigoPueblo = puebloSel.takeIf { it.contains(" - ") }?.split(" - ")?.getOrNull(0)
            val codigoCalle = loc.calleValor.toString()
            val numeroPoste = loc.delPoste.toString()

            actualizarUbicacionMapa(loc.latitud, loc.longitud, codigoPueblo, codigoCalle, numeroPoste)
            actualizarStreetView(loc.latitud, loc.longitud)

            binding.locationTitle.text = loc.direccion.ifBlank { getString(R.string.localizacion_overlay_title) }
            binding.locationCoordinates.text = getString(
                R.string.localizacion_coordenadas_format,
                loc.latitud,
                loc.longitud
            )
            binding.actionStreetView.isEnabled = true
            binding.actionCopy.isEnabled = true
            binding.actionCenter.isEnabled = true
            binding.actionNavigate.isEnabled = true
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
                if (puebloSeleccionado == getString(R.string.localizacion_select_pueblo)) return
                try {
                    val codigoPueblo = puebloSeleccionado.split(" - ")[0].toInt()
                    limpiarCamposDeTexto()
                    viewModel.cargarCallesParaPueblo(codigoPueblo)
                } catch (e: Exception) {
                    Log.e("Localizacion", "Error parseando pueblo seleccionado: ${e.message}", e)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun configurarControles() {
        binding.actionNavigate.setOnClickListener { mostrarOpcionesDeNavegacion() }
        binding.actionStreetView.setOnClickListener { abrirStreetView() }
        binding.actionCenter.setOnClickListener { centrarMapaEnLocalizacion() }
        binding.actionCopy.setOnClickListener { copiarCoordenadas() }
        binding.streetViewActionExpand.setOnClickListener { expandirStreetView() }
        binding.streetViewActionMinimize.setOnClickListener { minimizarStreetView() }
        binding.streetViewActionClose.setOnClickListener { cerrarStreetView() }
        binding.actionStreetView.isEnabled = false
        binding.actionCopy.isEnabled = false
        binding.actionCenter.isEnabled = false
        binding.actionNavigate.isEnabled = false
        actualizarStreetViewUi()
    }

    private fun configurarStreetViewSheet() {
        val behavior = BottomSheetBehavior.from(binding.streetViewSheet).also { streetViewBehavior = it }
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        streetViewMode = StreetViewMode.HIDDEN
        behavior.isHideable = true
        behavior.skipCollapsed = false
        behavior.isDraggable = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                streetViewMode = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> StreetViewMode.EXPANDED
                    BottomSheetBehavior.STATE_COLLAPSED -> StreetViewMode.COLLAPSED
                    BottomSheetBehavior.STATE_HIDDEN -> StreetViewMode.HIDDEN
                    else -> streetViewMode
                }
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    ensureStreetViewActive(false)
                    viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.CLOSED)
                } else {
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
        ensureStreetViewActive(true)
        streetViewMode = StreetViewMode.EXPANDED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        binding.streetViewSheet.alpha = 0f
        binding.streetViewSheet.animate().alpha(1f).setDuration(200).start()
        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }

    private fun expandirStreetView() {
        streetViewMode = StreetViewMode.EXPANDED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }

    private fun minimizarStreetView() {
        streetViewMode = StreetViewMode.COLLAPSED
        streetViewBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        actualizarStreetViewEstadoDesdeSheet()
        actualizarStreetViewUi()
    }

    private fun cerrarStreetView() {
        streetViewMode = StreetViewMode.HIDDEN
        streetViewBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        ensureStreetViewActive(false)
        viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.CLOSED)
        actualizarStreetViewUi()
    }

    private fun ensureStreetViewActive(active: Boolean) {
        if (active) {
            binding.streetViewPanorama.onStart()
            binding.streetViewPanorama.onResume()
        } else {
            binding.streetViewPanorama.onPause()
            binding.streetViewPanorama.onStop()
        }
    }

    private fun actualizarStreetViewEstadoDesdeSheet() {
        val estadoActual = viewModel.streetViewState.value
        val estado = when {
            streetViewMode == StreetViewMode.HIDDEN -> LocalizacionViewModel.StreetViewState.CLOSED
            estadoActual == LocalizacionViewModel.StreetViewState.LOADING -> LocalizacionViewModel.StreetViewState.LOADING
            streetViewHasPanorama && streetViewMode == StreetViewMode.EXPANDED ->
                LocalizacionViewModel.StreetViewState.FULLSCREEN
            streetViewHasPanorama && streetViewMode == StreetViewMode.COLLAPSED ->
                LocalizacionViewModel.StreetViewState.MINIMIZED
            streetViewHasPanorama.not() -> LocalizacionViewModel.StreetViewState.UNAVAILABLE
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
        binding.streetViewPanorama.isVisible = active && !unavailable
        binding.streetViewActionExpand.isVisible = estado == LocalizacionViewModel.StreetViewState.MINIMIZED
        binding.streetViewActionMinimize.isVisible = estado == LocalizacionViewModel.StreetViewState.FULLSCREEN
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

    private fun inicializarStreetView(savedInstanceState: Bundle?) {
        val streetBundle = savedInstanceState?.getBundle(CLAVE_STREET_VIEW_BUNDLE)
        binding.streetViewPanorama.onCreate(streetBundle)
        binding.streetViewPanorama.getStreetViewPanoramaAsync { panorama ->
            streetViewPanorama = panorama.apply {
                setUserNavigationEnabled(true)
                setPanningGesturesEnabled(true)
                setZoomGesturesEnabled(true)
                setStreetNamesEnabled(true)
                setOnStreetViewPanoramaChangeListener { location ->
                    streetViewHasPanorama = true
                    actualizarStreetViewEstadoDesdeSheet()
                    actualizarStreetViewUi()
                }
            }
        }
        streetViewHasPanorama = false
        viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.CLOSED)
        actualizarStreetViewUi()
    }

    override fun onMapReady(map: GoogleMap) {
        mapaGoogle = map

        // Controles de UI
        mapaGoogle?.uiSettings?.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = false
            isZoomGesturesEnabled = true
            isTiltGesturesEnabled = true
            isCompassEnabled = true
            isRotateGesturesEnabled = true
            isScrollGesturesEnabledDuringRotateOrZoom = true
            isScrollGesturesEnabled = true
            isIndoorLevelPickerEnabled = true
        }
        mapaGoogle?.apply {
            isTrafficEnabled = true
            isBuildingsEnabled = true
            isIndoorEnabled = true
            setMinZoomPreference(6f)
            setMaxZoomPreference(21f)
            setOnMyLocationButtonClickListener {
                followLocationEnabled = true
                hasCenteredOnUser = false
                solicitarUbicacionActual(force = true)
                true
            }
        }

        configurarTipoDeMapa()
        setupCameraListeners()        // mueve/idle centralizado
        verificarPermisosUbicacion()  // si ya hay permiso, activa myLocation + updates
        centrarMapaEnCostaRica()
    }

    private fun configurarTipoDeMapa() {
        var hybrid = false
        mapaGoogle?.setOnMapClickListener {
            mapaGoogle?.mapType = if (hybrid) GoogleMap.MAP_TYPE_NORMAL else GoogleMap.MAP_TYPE_HYBRID
            hybrid = !hybrid
        }
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
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        if (!followLocationEnabled) {
            hasCenteredOnUser = false
        }
        solicitarUbicacionActual(force = true)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
        val map = mapaGoogle ?: return
        val ubicacion = LatLng(latitud, longitud)

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
                    .title(getString(R.string.localizacion_marker_title))
                    .snippet(snippetInfo)
            )
        } else {
            marcador?.apply {
                position = ubicacion
                snippet = snippetInfo
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
        configurarInfoWindowPersonalizado()
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

    private fun configurarInfoWindowPersonalizado() {
        mapaGoogle?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null
            override fun getInfoContents(marker: Marker): View {
                val v = layoutInflater.inflate(R.layout.custom_info_window, null)
                v.findViewById<TextView>(R.id.titulo).text = marker.title
                v.findViewById<TextView>(R.id.snippet).text = marker.snippet
                return v
            }
        })
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
        val loc = viewModel.localizacion.value
        val lat = loc?.latitud
        val lng = loc?.longitud
        if (lat == null || lng == null) {
            Snackbar.make(binding.root, getString(R.string.localizacion_toast_sin_ubicacion_mapa), Snackbar.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texto = getString(R.string.localizacion_coordenadas_format, lat, lng)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.localizacion_label_coordenadas), texto))
        Snackbar.make(binding.root, getString(R.string.localizacion_coords_copied), Snackbar.LENGTH_SHORT).show()
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
        if (streetViewMode != StreetViewMode.HIDDEN) {
            binding.streetViewPanorama.onStart()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (streetViewMode != StreetViewMode.HIDDEN) {
            binding.streetViewPanorama.onResume()
        }
        rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        // si ya teníamos permiso, asegúrate de que los updates sigan activos
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationAndStartUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        binding.streetViewPanorama.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        binding.mapView.onStop()
        binding.streetViewPanorama.onStop()
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
        binding.mapView.onDestroy()
        binding.streetViewPanorama.onDestroy()
        handler.removeCallbacksAndMessages(null)
        singleLocationToken?.cancel()
        singleLocationToken = null
        streetViewPanorama = null
        _binding = null
        mapaGoogle = null
        marcador = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
        binding.streetViewPanorama.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapaBundle = outState.getBundle(CLAVE_MAPA_VISTA_BUNDLE) ?: Bundle()
        binding.mapView.onSaveInstanceState(mapaBundle)
        outState.putBundle(CLAVE_MAPA_VISTA_BUNDLE, mapaBundle)
        val streetBundle = outState.getBundle(CLAVE_STREET_VIEW_BUNDLE) ?: Bundle()
        binding.streetViewPanorama.onSaveInstanceState(streetBundle)
        outState.putBundle(CLAVE_STREET_VIEW_BUNDLE, streetBundle)
    }

    private fun actualizarStreetView(latitud: Double, longitud: Double) {
        val panorama = streetViewPanorama ?: return
        viewModel.actualizarStreetViewEstado(LocalizacionViewModel.StreetViewState.LOADING)
        actualizarStreetViewUi()
        panorama.setPosition(LatLng(latitud, longitud), 50, StreetViewSource.OUTDOOR)
    }

    private fun actualizarStreetViewUi() {
        val state = viewModel.streetViewState.value ?: LocalizacionViewModel.StreetViewState.CLOSED
        renderizarStreetViewEstado(state)
    }

    private fun actualizarAutorrotacionRuntime(enabled: Boolean) {
        runtimeAutoRotateEnabled = enabled && autoRotatePreference
    }

    private fun debeAutorrotar(): Boolean = autoRotatePreference && runtimeAutoRotateEnabled

    private fun solicitarUbicacionActual(force: Boolean = false) {
        val fine = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

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
        lastLocationErrorAt = 0L
        lastLocationReceivedAt = SystemClock.elapsedRealtime()
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
        val position = CameraPosition.Builder(currentPosition)
            .target(latLng)
            .zoom(zoom)
            .build()
        if (animate) {
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position))
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }
        lastCompassBearing = normalizeBearing(position.bearing)
    }

    private fun mostrarToastUbicacionSiNecesario(mensaje: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLocationErrorAt >= LOCATION_ERROR_TOAST_INTERVAL_MS) {
            lastLocationErrorAt = now
            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
        }
    }
}
