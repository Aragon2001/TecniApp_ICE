package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

// ViewModel en su package correcto

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
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
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentLocalizacionBinding
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.max
import java.util.Locale

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
    private val marcadoresCalles = mutableListOf<Marker>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var uiPreferences: SharedPreferences
    private val CLAVE_MAPA_VISTA_BUNDLE = "ClaveMapaVistaBundle"

    // --- Permisos (Activity Result API) ---
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                enableMyLocationAndStartUpdates()
            } else {
                Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }

    // --- Sensores y rotación ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var autoRotatePreference = true
    private var runtimeAutoRotateEnabled = true  // autorrotación controlada por brújula/sensor
    private var isCompassTouched = false    // true cuando la orientación vuelve ~Norte (bearing≈0)
    private var userIsInteracting = false   // true mientras el usuario mueve la cámara

    // Brújula
    private val BEARING_THRESHOLD_DEG = 2f
    private val BEARING_MIN_INTERVAL_MS = 350L
    private val BEARING_SMOOTHING = 0.18f
    private var lastCompassBearing: Float? = null
    private var lastBearingUpdateAt = 0L

    // --- Handler para pequeñas demoras (re-activar autorrotación tras gestos, etc.) ---
    private val handler = Handler(Looper.getMainLooper())

    // --- Ubicación del usuario ---
    private var followLocationEnabled = false
    private var hasCenteredOnUser = false
    private var lastLocationErrorAt = 0L
    private var singleLocationToken: CancellationTokenSource? = null
    private val LOCATION_ERROR_TOAST_INTERVAL_MS = 10_000L
    private val PREF_AUTO_ROTATE = "pref_auto_rotate_enabled"
    private val PREF_FOLLOW_LOCATION = "pref_follow_location_enabled"

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.actualizarPreferenciaMostrarCalles(isChecked)
    }

    // --- Requests de ubicación ---
    private val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000 // 1s entre updates
    ).apply {
        setMinUpdateIntervalMillis(1000)
        setMaxUpdateDelayMillis(10_000)
        setWaitForAccurateLocation(true)
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
                mostrarToastUbicacionSiNecesario(getString(R.string.localizacion_toast_sin_disponibilidad))
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
        uiPreferences = requireContext().getSharedPreferences("localizacion_prefs", Context.MODE_PRIVATE)
        autoRotatePreference = uiPreferences.getBoolean(PREF_AUTO_ROTATE, true)
        runtimeAutoRotateEnabled = autoRotatePreference
        followLocationEnabled = uiPreferences.getBoolean(PREF_FOLLOW_LOCATION, false)

        // Sensores
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR).also {
            if (it == null) Log.e("Localizacion", "Sensor de rotación no disponible")
        }

        configurarObservers()
        configurarControles()
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

            binding.direccionTextView.text = "Dirección: ${loc.direccion}"
            binding.delposteTextView.text = "Del Poste: ${loc.delPoste}"
            binding.alposteTextView.text = "Al Poste: ${loc.alPoste}"
        }

        viewModel.marcadoresCalles.observe(viewLifecycleOwner) { marcadores ->
            val lista = marcadores.orEmpty()
            actualizarMarcadoresDeCalles(lista)
        }

        viewModel.mostrarTodasCalles.observe(viewLifecycleOwner) { mostrar ->
            val switch = binding.switchMostrarCalles
            if (switch.isChecked != mostrar) {
                switch.setOnCheckedChangeListener(null)
                switch.isChecked = mostrar
                switch.setOnCheckedChangeListener(switchListener)
            }
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
        binding.buttonNavegar.setOnClickListener { mostrarOpcionesDeNavegacion() }
        binding.buttonShare.setOnClickListener { compartirUbicacion() }
        binding.switchMostrarCalles.setOnCheckedChangeListener(switchListener)
        binding.switchAutoRotacion.apply {
            isChecked = autoRotatePreference
            setOnCheckedChangeListener { _, isChecked ->
                autoRotatePreference = isChecked
                uiPreferences.edit().putBoolean(PREF_AUTO_ROTATE, isChecked).apply()
                if (!isChecked) {
                    runtimeAutoRotateEnabled = false
                } else {
                    runtimeAutoRotateEnabled = true
                    lastCompassBearing = mapaGoogle?.cameraPosition?.bearing?.let { normalizeBearing(it) }
                }
            }
        }
        binding.switchSeguirUbicacion.apply {
            isChecked = followLocationEnabled
            setOnCheckedChangeListener { _, isChecked ->
                followLocationEnabled = isChecked
                uiPreferences.edit().putBoolean(PREF_FOLLOW_LOCATION, isChecked).apply()
                hasCenteredOnUser = false
                if (isChecked) {
                    solicitarUbicacionActual(force = true)
                }
            }
        }
    }

    // Reset de textos y cámara a vista país
    private fun limpiarCamposDeTexto() {
        binding.direccionTextView.text = "Dirección: N/A"
        binding.delposteTextView.text = "Del Poste: 0"
        binding.alposteTextView.text = "Al Poste: 0"
        centrarMapaEnCostaRica()
        hasCenteredOnUser = false
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
                actualizarAutorrotacionRuntime(false)
                lastCompassBearing = mapaGoogle?.cameraPosition?.bearing?.let { normalizeBearing(it) }
                lastBearingUpdateAt = SystemClock.elapsedRealtime()
                Log.d("Localizacion", "Gesto detectado → autorrotación OFF")
            }
        }

        // 2) Movimiento continuo (señal para activar autorrotación si giran el mapa explícitamente)
        map.setOnCameraMoveListener {
            val bearing = mapaGoogle?.cameraPosition?.bearing ?: return@setOnCameraMoveListener
            // Si el usuario realmente giró el mapa (bearing cambia), permitimos autorrotación posterior
            if (abs(bearing) > 1 && !userIsInteracting) {
                actualizarAutorrotacionRuntime(true)
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

            // Tras un gesto, reactivar autorrotación después de un breve tiempo (si no se tocó brújula)
            if (userIsInteracting) {
                userIsInteracting = false
                handler.postDelayed({
                    if (!isCompassTouched) {
                        actualizarAutorrotacionRuntime(true)
                        Log.d("Localizacion", "Autorrotación ON tras gesto (delay)")
                    }
                }, 3000)
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

        val icono = redimensionarIcono(R.drawable.poste, 150, 150)
        if (marcador == null) {
            marcador = map.addMarker(
                MarkerOptions()
                    .position(ubicacion)
                    .title("Ubicación exacta")
                    .snippet(snippetInfo)
                    .icon(icono)
            )
        } else {
            marcador?.apply {
                position = ubicacion
                snippet = snippetInfo
                setIcon(icono)
                showInfoWindow()
            }
        }

        val cameraPosition = CameraPosition.Builder()
            .target(ubicacion)
            .zoom(18f)
            .bearing(map.cameraPosition.bearing)
            .tilt(45f)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600, null)
        lastCompassBearing = normalizeBearing(cameraPosition.bearing)
        lastBearingUpdateAt = SystemClock.elapsedRealtime()
        configurarInfoWindowPersonalizado()
    }

    private fun actualizarMarcadoresDeCalles(marcadores: List<LocalizacionViewModel.MarcadorCalle>) {
        val map = mapaGoogle ?: return
        marcadoresCalles.forEach { it.remove() }
        marcadoresCalles.clear()

        if (marcadores.isEmpty()) {
            return
        }

        val icono = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        marcadores.forEach { data ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(data.latitud, data.longitud))
                    .title(data.titulo)
                    .snippet(data.snippet)
                    .icon(icono)
            )
            marker?.let { marcadoresCalles += it }
        }
    }

    private fun redimensionarIcono(drawableRes: Int, w: Int, h: Int): BitmapDescriptor {
        val bmp = BitmapFactory.decodeResource(resources, drawableRes)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, false)
        return BitmapDescriptorFactory.fromBitmap(scaled)
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
        val lat = viewModel.localizacion.value?.latitud
        val lng = viewModel.localizacion.value?.longitud
        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), "La ubicación no está disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val centerParam = String.format(Locale.US, "%f,%f", lat, lng)

        // URIs corregidas
        val fieldMapsUri = Uri.parse("arcgis-fieldmaps://?referenceContext=center&itemID=&center=$centerParam")
        val googleMapsUri = Uri.parse("google.navigation:q=$centerParam")
        val browserUri = Uri.parse("https://maps.google.com/?q=$centerParam")

        // Intents
        val fieldMapsIntent = Intent(Intent.ACTION_VIEW, fieldMapsUri).apply {
            setPackage("com.esri.fieldmaps")
        }

        val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)

        // Mostrar selector manual
        val opciones = arrayOf("Field Maps", "Google Maps", "Navegador web")

        AlertDialog.Builder(context)
            .setTitle("Abrir ubicación con...")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Field Maps
                        try {
                            startActivity(fieldMapsIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Field Maps no está instalado o el vínculo es inválido.", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> { // Google Maps
                        try {
                            startActivity(googleMapsIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google Maps no está disponible.", Toast.LENGTH_LONG).show()
                        }
                    }
                    2 -> { // Navegador
                        try {
                            startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir en el navegador.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun compartirUbicacion() {
        val loc = viewModel.localizacion.value
        val lat = loc?.latitud
        val lng = loc?.longitud
        val calle = loc?.direccion
        val codigoCalle = loc?.calleValor
        val puebloStr = binding.spinnerPueblos.selectedItem?.toString().orEmpty()
        val poste = loc?.delPoste

        val parts = puebloStr.split(" - ")
        if (lat == null || lng == null || calle == null || codigoCalle == null || parts.size != 2) {
            Toast.makeText(requireContext(), "No se puede compartir la ubicación. Intente de nuevo.", Toast.LENGTH_SHORT).show()
            return
        }
        val codigoPueblo = parts[0]
        val nombrePueblo = parts[1]

        val mensaje = """
            Compartiendo la ubicación desde TecniApp ICE:
            
            - Pueblo: (Código: $codigoPueblo) "$nombrePueblo" 
            - Calle: (Código: $codigoCalle) "$calle" 
            - Poste: "$poste"
            
            https://maps.google.com/?q=$lat,$lng
        """.trimIndent()

        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
        }, "Compartir ubicación con"))
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
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
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
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        binding.switchMostrarCalles.setOnCheckedChangeListener(null)
        binding.switchAutoRotacion.setOnCheckedChangeListener(null)
        binding.switchSeguirUbicacion.setOnCheckedChangeListener(null)
        handler.removeCallbacksAndMessages(null)
        singleLocationToken?.cancel()
        singleLocationToken = null
        _binding = null
        mapaGoogle = null
        marcadoresCalles.forEach { it.remove() }
        marcadoresCalles.clear()
        marcador = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapaBundle = outState.getBundle(CLAVE_MAPA_VISTA_BUNDLE) ?: Bundle()
        binding.mapView.onSaveInstanceState(mapaBundle)
        outState.putBundle(CLAVE_MAPA_VISTA_BUNDLE, mapaBundle)
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
        if (!hasCenteredOnUser || (followLocationEnabled && !userIsInteracting)) {
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
