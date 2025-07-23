package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

import LocalizacionViewModel
import android.Manifest
import android.content.Context
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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
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

class LocalizacionFragment : Fragment(), OnMapReadyCallback, SensorEventListener {

    private lateinit var binding: FragmentLocalizacionBinding
    private lateinit var viewModel: LocalizacionViewModel
    private lateinit var mapaVista: MapView
    private var marcador: Marker? = null
    private lateinit var mapaGoogle: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CLAVE_MAPA_VISTA_BUNDLE = "ClaveMapaVistaBundle"
    private val PERMISO_LOCALIZACION = 1000
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var isManualRotation = false // Indica si el usuario ha girado manualmente el mapa
private var isCompassTouched = false // Indica si el usuario ha tocado la brújula
private var isAutoRotateEnabled = true // Controla si la autorrotación está activa
    private var userIsInteracting = false
    private val handler = Handler(Looper.getMainLooper())


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflar el layout para el fragmento
        binding = FragmentLocalizacionBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this).get(LocalizacionViewModel::class.java)

        // Inicializar FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        configurarObservers()
        configurarListeners()
        inicializarMapaVista(savedInstanceState)

        // Cargar los pueblos desde el ViewModel
        viewModel.cargarPueblos()

        return binding.root
    }

    private fun configurarObservers() {
        // Obtener el ProgressBar desde el layout
        val progressBar = binding.progressBar

        // Observar los cambios en la lista de pueblos
        viewModel.pueblos.observe(viewLifecycleOwner, Observer { pueblos ->

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                pueblos
            )
            binding.spinnerPueblos.adapter = adapter

        })

        // Observar los cambios en la lista de calles
        viewModel.calles.observe(viewLifecycleOwner, Observer { calles ->
            limpiarCamposDeTexto()

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                calles
            )
            binding.spinnerCalles.adapter = adapter


            // Listener para el spinner de calles
            binding.spinnerCalles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val calleSeleccionada = parent?.getItemAtPosition(position).toString()

                    // Verificar si la calle seleccionada no es "Seleccione una calle"
                    if (calleSeleccionada != "Seleccione una calle") {
                        try {
                            val partesCalle = calleSeleccionada.split(" - ")
                            val codigoCalle = partesCalle[0].toInt()
                            val direccionCalle = partesCalle[1]
                            val puebloSeleccionado = binding.spinnerPueblos.selectedItem.toString()

                            // Asegurarse de que se seleccionó un pueblo válido
                            if (puebloSeleccionado != "Seleccione un pueblo") {
                                val codigoPueblo = puebloSeleccionado.split(" - ")[0].toInt()

                                // Cargar localización para la calle seleccionada
                                viewModel.cargarLocalizacionParaCalle(codigoCalle, codigoPueblo, direccionCalle)

                                // Actualizar el mapa solo si la localización está disponible
                                viewModel.localizacion.observe(viewLifecycleOwner, Observer { localizacion ->
                                    if (localizacion.latitud != null && localizacion.longitud != null) {
                                        val numeroPoste = localizacion.delPoste
                                        actualizarUbicacionMapa(localizacion.latitud, localizacion.longitud, codigoPueblo.toString(), partesCalle[0], numeroPoste.toString())

                                        binding.direccionTextView.text = "Dirección: ${localizacion.direccion ?: "N/A"}"
                                        binding.delposteTextView.text = "Del Poste: ${localizacion.delPoste ?: 0}"
                                        binding.alposteTextView.text = "Al Poste: ${localizacion.alPoste ?: 0}"
                                    }
                                })
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("LocalizacionFragment", "Error al convertir el código de la calle: ${e.message}")
                        }
                    } else {
                        Log.d("LocalizacionFragment", "Calle seleccionada es inválida: $calleSeleccionada")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No hacer nada si no se selecciona ninguna calle
                }
            }
        })

        // Observar los errores y mostrar mensajes
        viewModel.estado.observe(viewLifecycleOwner, Observer { estado ->
            when (estado) {
                is LocalizacionViewModel.Estado.Error -> {
                    // Ocultar el ProgressBar y mostrar el mensaje de error
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_SHORT).show()
                }
                is LocalizacionViewModel.Estado.Cargando -> {
                    // Mostrar el ProgressBar cuando el estado sea "Cargando"
                    progressBar.visibility = View.VISIBLE
                }
                is LocalizacionViewModel.Estado.Exito -> {
                    // Ocultar el ProgressBar cuando el estado sea "Exito"
                    progressBar.visibility = View.GONE
                }
                else -> {
                    // Ocultar el ProgressBar por defecto
                    progressBar.visibility = View.GONE
                }
            }
        })

        // Listener para el spinner de pueblos
        binding.spinnerPueblos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val puebloSeleccionado = parent?.getItemAtPosition(position).toString()

                // Asegurarse de que se seleccionó un pueblo válido
                if (puebloSeleccionado != "Seleccione un pueblo") {
                    val codigoPueblo = puebloSeleccionado.split(" - ")[0].toInt()

                    // Limpiar los campos de texto cuando se seleccione un pueblo nuevo
                    limpiarCamposDeTexto()

                    // Cargar las calles para el pueblo seleccionado
                    viewModel.cargarCallesParaPueblo(codigoPueblo)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No hacer nada si no se selecciona ningún pueblo
            }
        }
    }

    // Función para limpiar los campos de texto
    private fun limpiarCamposDeTexto() {
        if (::mapaGoogle.isInitialized) {
        binding.direccionTextView.text = "Dirección: N/A"
        binding.delposteTextView.text = "Del Poste: 0"
        binding.alposteTextView.text = "Al Poste: 0"
          // Inicializar el mapa en Costa Rica
        val costaRicaLatLng = LatLng(9.7489, -83.7534)  // Coordenadas de Costa Rica
        val zoomNivel = 7f  // Ajusta el nivel de zoom (por ejemplo, 7 para vista nacional)
         // Centrar el mapa en Costa Rica y hacer zoom
        mapaGoogle.moveCamera(CameraUpdateFactory.newLatLngZoom(costaRicaLatLng, zoomNivel))
                } else {
        Log.e("LocalizacionFragment", "mapaGoogle no está inicializado. No se pueden limpiar los campos de texto.")
    }

    }




    private fun configurarListeners() {
        // Listener para el spinner de pueblos
        binding.spinnerPueblos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val puebloSeleccionado = parent?.getItemAtPosition(position).toString()

                // Verificar si el pueblo seleccionado es válido
                if (puebloSeleccionado != "Seleccione un pueblo") {
                    try {
                        // Extraer el código del pueblo (la primera parte antes del " - ")
                        val codigoPueblo = puebloSeleccionado.split(" - ")[0].toInt()

                        // Llamar al método para cargar las calles para el pueblo seleccionado
                        viewModel.cargarCallesParaPueblo(codigoPueblo)
                    } catch (e: NumberFormatException) {
                        // Manejar el caso de que el código del pueblo no sea un número válido
                        Log.e("LocalizacionFragment", "Error al convertir el código del pueblo: ${e.message}")
                    }
                } else {
                    // Si el pueblo seleccionado es "Seleccione un pueblo", no hacer nada
                    Log.d("LocalizacionFragment", "Pueblo seleccionado es inválido: $puebloSeleccionado")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No hacer nada si no se selecciona ningún pueblo
            }
        }




        // Listener para el botón de navegación
        binding.buttonNavegar.setOnClickListener {
            mostrarOpcionesDeNavegacion()
        }

        // Listener para el botón de compartir ubicación
        binding.buttonShare.setOnClickListener {
            compartirUbicacion()
        }

        // Listener para el botón de mostrar todas las calles
        binding.buttonAll.setOnClickListener {
            //cargarCallesDelPueblo()
        }


    }

       // Método para inicializar el mapa y gestionar el ciclo de vida del MapView
    private fun inicializarMapaVista(savedInstanceState: Bundle?) {
        val mapaVistaBundle: Bundle? = savedInstanceState?.getBundle(CLAVE_MAPA_VISTA_BUNDLE)

        mapaVista = binding.mapView // Asumiendo que estás utilizando View Binding
        mapaVista.onCreate(mapaVistaBundle)
        mapaVista.getMapAsync(this) // Asignar el callback del mapa
    }

    // Este método se ejecuta cuando el mapa está listo
override fun onMapReady(mapa: GoogleMap) {
    // Inicializa el mapa una vez que esté listo
    mapaGoogle = mapa

    // Inicializar el SensorManager dentro de un Fragment
    sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Configurar el listener para detectar cambios manuales en la cámara
    setupCameraChangeListener()

    // Obtener el sensor de rotación
    rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (rotationVectorSensor == null) {
        Log.e("SensorError", "El sensor de rotación no está disponible")
    } else {
        Log.d("SensorInfo", "El sensor de rotación está disponible")
    }

    // Configurar ventana de información personalizada (si es necesario)
    configurarInfoWindowPersonalizado()

    // Configuración de los controles del mapa
    with(mapaGoogle.uiSettings) {
        isZoomControlsEnabled = true
        isMyLocationButtonEnabled = true
        isMapToolbarEnabled = true
        isZoomGesturesEnabled = true
        isTiltGesturesEnabled = true
        isCompassEnabled = true
        isRotateGesturesEnabled = true
        isScrollGesturesEnabledDuringRotateOrZoom = true
        isScrollGesturesEnabled = true
    }

    // Activar el tráfico en el mapa
    mapaGoogle.isTrafficEnabled = true

    // Configurar el tipo de mapa y el clic en el mapa
    configurarTipoDeMapa()

    // Verificar los permisos de ubicación y habilitar la ubicación en el mapa
    verificarPermisosUbicacion()

    // Inicializar el mapa en Costa Rica con un nivel de zoom adecuado
    centrarMapaEnCostaRica()


   // Configurar el listener para detectar cambios manuales en la cámara
    configurarCameraMoveListener()

    // Configurar comportamiento de la brújula
    configurarBrújula()

    // Configurar la rotación automática y brújula
    configurarBrújulaYRotación()
}


    // Configurar el tipo de mapa y el clic en el mapa
    private fun configurarTipoDeMapa() {
        var isHybridMode = false
        mapaGoogle.setOnMapClickListener {
            // Cambiar entre modo normal y satélite al hacer clic en el mapa
            mapaGoogle.mapType = if (isHybridMode) {
                GoogleMap.MAP_TYPE_NORMAL
            } else {
                GoogleMap.MAP_TYPE_HYBRID
            }
            isHybridMode = !isHybridMode // Alternar el estado
        }
    }

    // Verificar permisos de ubicación
    private fun verificarPermisosUbicacion() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Solicitar permisos si no están concedidos
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISO_LOCALIZACION)
        } else {
            // Si los permisos ya están concedidos, habilitar la ubicación
            mapaGoogle.isMyLocationEnabled = true
            obtenerUbicacionActual()
        }
    }

    // Centrar el mapa en Costa Rica
    private fun centrarMapaEnCostaRica() {
        val costaRicaLatLng = LatLng(9.7489, -83.7534)  // Coordenadas de Costa Rica
        val zoomNivel = 7f  // Nivel de zoom
        mapaGoogle.moveCamera(CameraUpdateFactory.newLatLngZoom(costaRicaLatLng, zoomNivel))
    }


private val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
    Priority.PRIORITY_HIGH_ACCURACY,
    1000  // Intervalo de 1 segundo entre actualizaciones
).apply {
    setMinUpdateIntervalMillis(1000) // Mínimo 1 segundo entre actualizaciones
    setMaxUpdateDelayMillis(10000)   // Máximo 10 segundos de retraso
}.build()

private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        val location: Location? = locationResult.lastLocation
        if (location != null) {
            val ubicacionActual = LatLng(location.latitude, location.longitude)
            // Mover la cámara a `ubicacionActual`
        } else {
            Toast.makeText(requireContext(), "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
        if (!locationAvailability.isLocationAvailable) {
            Toast.makeText(requireContext(), "La ubicación no está disponible", Toast.LENGTH_SHORT).show()
        }
    }
}

    private fun obtenerUbicacionActual() {
    if (ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISO_LOCALIZACION
        )
        return
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )
}

private fun detenerUbicacion() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
}



    // Método para redimensionar el ícono del marcador
    private fun redimensionarIcono(drawableRes: Int, ancho: Int, alto: Int): BitmapDescriptor {
        val imageBitmap = BitmapFactory.decodeResource(resources, drawableRes)
        val redimensionado = Bitmap.createScaledBitmap(imageBitmap, ancho, alto, false)
        return BitmapDescriptorFactory.fromBitmap(redimensionado)
    }

    // Método para actualizar la ubicación en el mapa sin crear un nuevo marcador
    private fun actualizarUbicacionMapa(
        latitud: Double?,
        longitud: Double?,
        codigoPueblo: String?,
        numeroCalle: String?,
        numeroPoste: String?
    ) {
        if (latitud != null && longitud != null) {
            val ubicacion = LatLng(latitud, longitud)

            // Ajustar el código del pueblo, número de calle y número de poste con ceros al frente
            val snippetInfo = buildString {
                append(codigoPueblo?.padStart(4, '0') ?: "0000")
                append("-")
                append(numeroCalle?.padStart(3, '0') ?: "000")
                append("-")
                append(numeroPoste?.padStart(3, '0') ?: "000")
                append("-00")
            }

            // Redimensionar ícono personalizado
            val iconoPersonalizado = redimensionarIcono(R.drawable.poste, 150, 150)

            // Actualizar o crear el marcador
            if (marcador != null) {
                actualizarMarcador(ubicacion, snippetInfo, iconoPersonalizado)
            } else {
                crearNuevoMarcador(ubicacion, snippetInfo, iconoPersonalizado)
            }

            // Mover la cámara al nuevo marcador
            mapaGoogle.moveCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, 18f))
        }
    }

    // Método para actualizar el marcador existente
    private fun actualizarMarcador(ubicacion: LatLng, snippetInfo: String, iconoPersonalizado: BitmapDescriptor) {
        marcador?.apply {
            position = ubicacion
            snippet = snippetInfo
            setIcon(iconoPersonalizado)
            showInfoWindow()
        }
    }

    // Método para crear un nuevo marcador
    private fun crearNuevoMarcador(ubicacion: LatLng, snippetInfo: String, iconoPersonalizado: BitmapDescriptor) {
        marcador = mapaGoogle.addMarker(
            MarkerOptions()
                .position(ubicacion)
                .title("Ubicación exacta")
                .snippet(snippetInfo)
                .icon(iconoPersonalizado)
        )
    }

    // Configurar InfoWindowAdapter para fondo translúcido
    private fun configurarInfoWindowPersonalizado() {
        mapaGoogle.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                // Usar null para el fondo predeterminado, pero personalizable
                return null
            }

            override fun getInfoContents(marker: Marker): View? {
                // Inflar un layout personalizado para el contenido del InfoWindow
                val vista = layoutInflater.inflate(R.layout.custom_info_window, null)

                // Obtener los elementos del layout
                val tituloTextView = vista.findViewById<TextView>(R.id.titulo)
                val snippetTextView = vista.findViewById<TextView>(R.id.snippet)

                // Asignar el título y el snippet del marcador
                tituloTextView.text = marker.title
                snippetTextView.text = marker.snippet

                return vista
            }
        })
    }


    // Gestión de permisos de ubicación
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISO_LOCALIZACION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Si el permiso fue concedido, habilitar la ubicación
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mapaGoogle.isMyLocationEnabled = true
                    obtenerUbicacionActual()
                }
            } else {
                // Mostrar un mensaje si el permiso no fue concedido
                Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarOpcionesDeNavegacion() {
    // Obtener la latitud y longitud desde el ViewModel
    val latitud = viewModel.localizacion.value?.latitud
    val longitud = viewModel.localizacion.value?.longitud

    if (latitud != null && longitud != null) {
        // Crear Intent para Google Maps
        val gmmIntentUri = Uri.parse("google.navigation:q=$latitud,$longitud")
        val gmmIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        gmmIntent.setPackage("com.google.android.apps.maps") // Especificar Google Maps

        // Crear Intent para Waze
        val wazeUri = Uri.parse("waze://?ll=$latitud,$longitud&navigate=yes")
        val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri)
        wazeIntent.setPackage("com.waze") // Especificar Waze

        // Lista de Intents (Google Maps y Waze)
        val intentList = mutableListOf<Intent>()
        intentList.add(gmmIntent)
        intentList.add(wazeIntent)

        // Crear el Intent de selección (Chooser)
        val chooserIntent = Intent.createChooser(intentList[0], "Selecciona tu aplicación de navegación")

        // Agregar un mensaje adicional (puedes cambiar el texto si lo deseas)
        chooserIntent.putExtra(Intent.EXTRA_TEXT, "Elige cómo quieres navegar a esta ubicación:")

        // Incluir las opciones de Waze y Google Maps solo
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toTypedArray())

        // Mostrar el chooser con el mensaje adicional
        startActivity(chooserIntent)
    } else {
        Toast.makeText(requireContext(), "La ubicación no está disponible", Toast.LENGTH_SHORT).show()
    }
}


    private fun compartirUbicacion() {
    // Recuperar valores de la localización
    val latitud = viewModel.localizacion.value?.latitud
    val longitud = viewModel.localizacion.value?.longitud
    val calle = viewModel.localizacion.value?.direccion // Suponiendo que este campo tiene la dirección
    val codigoCalle = viewModel.localizacion.value?.calleValor // Asegúrate de que este campo exista
    val puebloSeleccionado = binding.spinnerPueblos.selectedItem.toString()
val numeroPoste=viewModel.localizacion.value?.delPoste

    // Verificar que el pueblo seleccionado tenga el formato esperado "Código - Pueblo"
    val puebloParts = puebloSeleccionado.split(" - ")
    if (puebloParts.size != 2) {
        Toast.makeText(requireContext(), "El pueblo seleccionado no tiene un formato válido.", Toast.LENGTH_SHORT).show()
        return
    }

    val codigoPueblo = puebloParts[0] // Obtener el código del pueblo
    val nombrePueblo = puebloParts[1] // Obtener el nombre del pueblo

    // Comprobar si todos los valores necesarios están presentes
    if (latitud != null && longitud != null && calle != null && codigoCalle != null) {

        val mensaje = """
            Compartiendo la ubicación desde TecniApp ICE:
            
            - Pueblo: (Código: $codigoPueblo) "$nombrePueblo" 
            
            - Calle: (Código: $codigoCalle) "$calle" 
            
            - Poste: "$numeroPoste"
            
            
            https://maps.google.com/?q=$latitud,$longitud
        """.trimIndent()

        // Crear intent para compartir la ubicación
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, mensaje)

        // Abrir el selector de aplicaciones para compartir
        startActivity(Intent.createChooser(intent, "Compartir ubicación con"))
    } else {
        // Mostrar mensaje si falta algún dato necesario
        Toast.makeText(requireContext(), "No se puede compartir la ubicación. Intente de nuevo.", Toast.LENGTH_SHORT).show()
    }
}

    private fun configurarBrújula() {
        mapaGoogle.setOnCameraIdleListener {
            // Si el mapa está orientado al norte (bearing == 0) y la brújula fue tocada, desactiva la autorrotación
            if (mapaGoogle.cameraPosition.bearing == 0f) {
                isCompassTouched = true
                isAutoRotateEnabled = false // Desactiva la autorrotación solo al tocar la brújula
                Log.d("Compass", "La brújula fue tocada. Autorrotación desactivada.")
            }
        }
    }





private fun configurarBrújulaYRotación() {
    // Listener para detectar movimientos de la cámara
    mapaGoogle.setOnCameraMoveListener {
        val currentBearing = mapaGoogle.cameraPosition.bearing

        // Si el usuario ha girado manualmente el mapa (bearing > 1), activar la autorrotación
        if (Math.abs(currentBearing) > 1 && !userIsInteracting) {
            isAutoRotateEnabled = true // Activa la autorrotación al girar el mapa
            Log.d("AutoRotate", "Autorrotación activada al girar manualmente el mapa.")
        }
    }

    // Listener para cuando la cámara está inactiva (brújula o fin del gesto)
    mapaGoogle.setOnCameraIdleListener {
        val currentBearing = mapaGoogle.cameraPosition.bearing

        // Si el mapa está orientado al norte (cerca de 0°), desactiva la autorrotación
        if (Math.abs(currentBearing) < 1 && isCompassTouched) {
            isAutoRotateEnabled = false
            isCompassTouched = false // Reinicia el estado de la brújula
            Log.d("AutoRotate", "Autorrotación desactivada al presionar la brújula.")
        }
    }
}




  private fun configurarCameraMoveListener() {
    mapaGoogle.setOnCameraMoveStartedListener { reason ->
        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            userIsInteracting = true
            isAutoRotateEnabled = false // Desactiva la autorrotación durante la interacción manual
            Log.d("MapInteraction", "Interacción manual detectada. Autorrotación desactivada.")
        }
    }

    mapaGoogle.setOnCameraIdleListener {
        if (userIsInteracting) {
            userIsInteracting = false
            // Después de la interacción manual, activa la autorrotación si no se tocó la brújula
            handler.postDelayed({
                if (!isCompassTouched) {
                    isAutoRotateEnabled = true
                    Log.d("MapInteraction", "Autorrotación reactivada después de la interacción manual.")
                }
            }, 3000) // 3 segundos de espera
        }
    }
}




    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR && isAutoRotateEnabled && !isCompassTouched) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val bearing = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            val currentBearing = mapaGoogle.cameraPosition.bearing
            // Solo actualiza si el cambio es significativo y no hay interacción manual
            if (Math.abs(currentBearing - bearing) > 1 && !userIsInteracting) {
                val cameraPosition = CameraPosition.Builder()
                    .target(mapaGoogle.cameraPosition.target)
                    .zoom(mapaGoogle.cameraPosition.zoom)
                    .bearing(bearing)
                    .build()

                mapaGoogle.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
    }



    // Método para alternar la autorrotación manualmente (puedes llamarlo desde algún botón si lo deseas)
private fun toggleAutoRotate() {
    isAutoRotateEnabled = !isAutoRotateEnabled
    if (isAutoRotateEnabled) {
        // Restablecer la cámara al último bearing
        val cameraPosition = CameraPosition.Builder()
            .target(mapaGoogle.cameraPosition.target)
            .zoom(mapaGoogle.cameraPosition.zoom)
            .bearing(mapaGoogle.cameraPosition.bearing) // Mantener la orientación actual
            .build()
        mapaGoogle.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }
}

// Método para configurar el listener de cambios en la cámara (para detectar la brújula)
private fun setupCameraChangeListener() {
    mapaGoogle.setOnCameraMoveListener {
        // Detectar si el bearing es cercano a 0 (norte), lo que indica que se presionó la brújula
        val currentBearing = mapaGoogle.cameraPosition.bearing
        if (Math.abs(currentBearing) < 1) {
            // Si el mapa está orientado al norte, desactivar la autorrotación
            isAutoRotateEnabled = false
        }
    }
}




  override fun onStart() {
    super.onStart()
    mapaVista.onStart()
}

override fun onResume() {
    super.onResume()
    mapaVista.onResume()
    obtenerUbicacionActual()
       rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)

}}

override fun onPause() {
    super.onPause()
    mapaVista.onPause()
    detenerUbicacion()
    fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
}

override fun onStop() {
    super.onStop()
    mapaVista.onStop()
}

override fun onDestroy() {
    super.onDestroy()
    mapaVista.onDestroy()
}

override fun onLowMemory() {
    super.onLowMemory()
    mapaVista.onLowMemory()
}
     override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // Puedes manejar cambios en la precisión del sensor aquí
    when (accuracy) {
        SensorManager.SENSOR_STATUS_NO_CONTACT -> {
            Log.w("SensorAccuracy", "Sin contacto con el sensor")
        }
        SensorManager.SENSOR_STATUS_UNRELIABLE -> {
            Log.w("SensorAccuracy", "Precisión no confiable del sensor")
        }

        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
            Log.i("SensorAccuracy", "Precisión baja del sensor")
        }
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> {
            Log.i("SensorAccuracy", "Precisión media del sensor")
        }
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
            Log.i("SensorAccuracy", "Precisión alta del sensor")
        }
        else -> {
            Log.d("SensorAccuracy", "Precisión desconocida")
        }
    }
}


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapaVistaBundle = outState.getBundle(CLAVE_MAPA_VISTA_BUNDLE) ?: Bundle()
        mapaVista.onSaveInstanceState(mapaVistaBundle)
        outState.putBundle(CLAVE_MAPA_VISTA_BUNDLE, mapaVistaBundle)

    }
}
