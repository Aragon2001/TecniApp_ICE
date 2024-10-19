package com.Arasoftsolutions.tecniapp_ice.ui.localizacion

import LocalizacionViewModel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class LocalizacionFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentLocalizacionBinding
    private lateinit var viewModel: LocalizacionViewModel
    private lateinit var mapaVista: MapView
    private var marcador: Marker? = null
    private lateinit var mapaGoogle: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CLAVE_MAPA_VISTA_BUNDLE = "ClaveMapaVistaBundle"
    private val PERMISO_LOCALIZACION = 1000


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

        // Listener para el botón de compartir mostrar todas las calles
        binding.buttonAll.setOnClickListener {
           // cargarCallesDelPueblo()
        }
    }

    // Método para inicializar el mapa y gestionar el ciclo de vida del MapView
    private fun inicializarMapaVista(savedInstanceState: Bundle?) {
        val mapaVistaBundle: Bundle? = savedInstanceState?.getBundle(CLAVE_MAPA_VISTA_BUNDLE)

        mapaVista = binding.mapView
        mapaVista.onCreate(mapaVistaBundle)
        mapaVista.getMapAsync(this)
    }

    // Este método se ejecuta cuando el mapa está listo
    override fun onMapReady(mapa: GoogleMap) {
        // Inicializa el mapa una vez que esté listo
        mapaGoogle = mapa

        // Configurar ventana de información personalizada (si es necesario)
        configurarInfoWindowPersonalizado()

        // Configuración de los controles del mapa
        with(mapaGoogle.uiSettings) {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = true
            isZoomGesturesEnabled = true
            isTiltGesturesEnabled = true
            isScrollGesturesEnabled = true
        }

        // Activar el tráfico en el mapa
        mapaGoogle.isTrafficEnabled = true

        // Verificar los permisos de ubicación
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Solicitar permisos si no están concedidos
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISO_LOCALIZACION)
        } else {
            // Si los permisos ya están concedidos, habilitar la ubicación
            mapaGoogle.isMyLocationEnabled = true
            obtenerUbicacionActual()
        }

        // Inicializar el mapa en Costa Rica con un nivel de zoom adecuado
        val costaRicaLatLng = LatLng(9.7489, -83.7534)  // Coordenadas de Costa Rica
        val zoomNivel = 7f  // Nivel de zoom

        // Centrar el mapa en Costa Rica
        mapaGoogle.moveCamera(CameraUpdateFactory.newLatLngZoom(costaRicaLatLng, zoomNivel))
    }


    private fun obtenerUbicacionActual() {
        // Configuración para solicitar actualizaciones de ubicación
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,  // Precisión alta
            5000  // Intervalo de 5 segundos (ajústalo según tus necesidades)
        ).apply {
            setMinUpdateIntervalMillis(1000) // Mínimo 1 segundo entre actualizaciones
            setMaxUpdateDelayMillis(10000) // Máximo 10 segundos de retraso
        }.build()

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Solicitar permisos de ubicación si no están concedidos
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISO_LOCALIZACION)
            return
        }

        // Solicitar actualizaciones de ubicación en tiempo real
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location: Location? = locationResult.lastLocation
                if (location != null) {
                    // Mover la cámara a la ubicación actual
                    val ubicacionActual = LatLng(location.latitude, location.longitude)

                } else {
                    // Mostrar mensaje si no se puede obtener la ubicación
                    Toast.makeText(requireContext(), "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                super.onLocationAvailability(locationAvailability)
                if (!locationAvailability.isLocationAvailable) {
                    // Mostrar mensaje si la ubicación no está disponible
                    Toast.makeText(requireContext(), "La ubicación no está disponible", Toast.LENGTH_SHORT).show()
                }
            }
        }, Looper.getMainLooper())
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


  override fun onStart() {
    super.onStart()
    mapaVista.onStart()
}

override fun onResume() {
    super.onResume()
    mapaVista.onResume()
}

override fun onPause() {
    super.onPause()
    mapaVista.onPause()
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


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapaVistaBundle = outState.getBundle(CLAVE_MAPA_VISTA_BUNDLE) ?: Bundle()
        mapaVista.onSaveInstanceState(mapaVistaBundle)
        outState.putBundle(CLAVE_MAPA_VISTA_BUNDLE, mapaVistaBundle)
    }
}
