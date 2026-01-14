package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetMapPickerBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap

import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar

/**
 * BottomSheet interactivo para seleccionar coordenadas desde un mapa de Google.
 *
 * Uso básico desde un Fragment:
 * ```kotlin
 * parentFragmentManager.setFragmentResultListener(
 *     MapCoordinatePickerBottomSheet.REQUEST_KEY,
 *     viewLifecycleOwner
 * ) { _, bundle ->
 *     val lat = bundle.getDouble(MapCoordinatePickerBottomSheet.RESULT_LAT)
 *     val lng = bundle.getDouble(MapCoordinatePickerBottomSheet.RESULT_LNG)
 *     // Utiliza las coordenadas recibidas
 * }
 *
 * MapCoordinatePickerBottomSheet.newInstance(latitudInicial, longitudInicial)
 *     .show(parentFragmentManager, MapCoordinatePickerBottomSheet.TAG)
 * ```
 */
class MapCoordinatePickerBottomSheet : BottomSheetDialogFragment(), OnMapReadyCallback {

    private var _binding: BottomsheetMapPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng = DEFAULT_LATLNG
    private var currentMapTypeIndex = 0
    private var shouldRequestMyLocation = true

    private val mapTypes = intArrayOf(
        GoogleMap.MAP_TYPE_NORMAL,
        GoogleMap.MAP_TYPE_TERRAIN,
        GoogleMap.MAP_TYPE_SATELLITE
    )

    private val mapTypeLabels = intArrayOf(
        R.string.map_picker_map_type_normal,
        R.string.map_picker_map_type_terrain,
        R.string.map_picker_map_type_satellite
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.any { it.value }
            if (granted) {
                enableMyLocationLayer()
                moveToMyLocation()
            } else {
                showSnackbar(R.string.map_picker_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val restoredLat = savedInstanceState?.getDouble(STATE_SELECTED_LATITUDE)
            ?: arguments?.getDouble(ARG_LATITUDE)?.takeIf { !it.isNaN() }
        val restoredLng = savedInstanceState?.getDouble(STATE_SELECTED_LONGITUDE)
            ?: arguments?.getDouble(ARG_LONGITUDE)?.takeIf { !it.isNaN() }
        if (restoredLat != null && restoredLng != null) {
            selectedLatLng = LatLng(restoredLat, restoredLng)
        }
        shouldRequestMyLocation = restoredLat == null || restoredLng == null

        currentMapTypeIndex = savedInstanceState?.getInt(STATE_MAP_TYPE_INDEX) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener { dialogInterface ->
                val dialog = dialogInterface as BottomSheetDialog
                val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let { sheet ->
                    sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    BottomSheetBehavior.from(sheet).apply {
                        skipCollapsed = true
                        isFitToContents = true
                        state = BottomSheetBehavior.STATE_EXPANDED
                        isDraggable = false
                        isHideable = false
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetMapPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()

        val mapViewBundle = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)
        binding.mapView.onCreate(mapViewBundle)
        binding.mapView.getMapAsync(this)

        if (shouldRequestMyLocation) {
            handleMyLocationRequest()
        }

        binding.btnCancelar.setOnClickListener { dismiss() }
        binding.btnConfirmar.setOnClickListener {
            val result = selectedLatLng
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_LAT to result.latitude,
                    RESULT_LNG to result.longitude
                )
            )
            dismiss()
        }

        updateCoordinateLabels()
    }

    private fun setupToolbar() = with(binding.toolbar) {
        title = getString(R.string.map_picker_title)
        navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close_sheet)
        navigationContentDescription = getString(android.R.string.cancel)
        setNavigationOnClickListener { dismiss() }
        if (menu.size() == 0) {
            inflateMenu(R.menu.map_picker_menu)
        }
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_my_location -> {
                    handleMyLocationRequest()
                    true
                }
                R.id.action_toggle_map_type -> {
                    cycleMapType()
                    true
                }

                else -> false
            }
        }
    }

    private fun handleMyLocationRequest() {
        if (hasLocationPermission()) {
            moveToMyLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun cycleMapType() {
        currentMapTypeIndex = (currentMapTypeIndex + 1) % mapTypes.size
        applyMapType()
        val labelRes = mapTypeLabels[currentMapTypeIndex]
        showSnackbar(getString(R.string.map_picker_map_type_changed, getString(labelRes)))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = true
            isMyLocationButtonEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isIndoorLevelPickerEnabled = true
        }

        map.setOnCameraIdleListener {
            selectedLatLng = map.cameraPosition.target
            updateCoordinateLabels()
        }

        val cameraPosition = CameraPosition.Builder()
            .target(selectedLatLng)
            .zoom(DEFAULT_ZOOM)
            .tilt(45f)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

        applyMapType()
        enableMyLocationLayer()
        if (shouldRequestMyLocation) {
            moveToMyLocation()
        }
    }

    private fun applyMapType() {
        val map = googleMap ?: return
        val mapType = mapTypes[currentMapTypeIndex]
        map.mapType = mapType
    }

    private fun updateCoordinateLabels() {
        binding.latitudeValue.text = getString(R.string.map_picker_latitude, selectedLatLng.latitude)
        binding.longitudeValue.text = getString(R.string.map_picker_longitude, selectedLatLng.longitude)
    }

    private fun hasLocationPermission(): Boolean {
        val context = requireContext()
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun showSnackbar(messageRes: Int) {
        view?.let { Snackbar.make(it, messageRes, Snackbar.LENGTH_LONG).show() }
    }

    private fun showSnackbar(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showLocationLoading(isLoading: Boolean) {
        binding.toolbar.subtitle = if (isLoading) {
            getString(R.string.map_picker_loading_location)
        } else {
            null
        }
    }

    private fun moveToMyLocation() {
        if (!hasLocationPermission()) return
        showLocationLoading(true)
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED && context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val target = LatLng(location.latitude, location.longitude)
                    animateCamera(target)
                    showLocationLoading(false)
                } else {
                    requestCurrentLocation()
                }
            }
            .addOnFailureListener {
                showLocationLoading(false)
                showSnackbar(R.string.map_picker_location_unavailable)
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val target = LatLng(location.latitude, location.longitude)
                    animateCamera(target)
                } else {
                    showSnackbar(R.string.map_picker_location_unavailable)
                }
            }
            .addOnFailureListener {
                showSnackbar(R.string.map_picker_location_unavailable)
            }
            .addOnCompleteListener {
                showLocationLoading(false)
            }
    }

    private fun animateCamera(target: LatLng) {
        selectedLatLng = target
        updateCoordinateLabels()
        googleMap?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(DEFAULT_ZOOM)
                    .tilt(45f)
                    .build()
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        if (hasLocationPermission()) {
            googleMap?.isMyLocationEnabled = true
        } else {
            googleMap?.isMyLocationEnabled = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY) ?: Bundle().also {
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, it)
        }
        binding.mapView.onSaveInstanceState(mapViewBundle)
        outState.putDouble(STATE_SELECTED_LATITUDE, selectedLatLng.latitude)
        outState.putDouble(STATE_SELECTED_LONGITUDE, selectedLatLng.longitude)
        outState.putInt(STATE_MAP_TYPE_INDEX, currentMapTypeIndex)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.mapView.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        binding.mapView.onDestroy()
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    companion object {
        const val REQUEST_KEY = "map_picker_result"
        const val RESULT_LAT = "result_latitude"
        const val RESULT_LNG = "result_longitude"
        const val TAG = "MapCoordinatePickerBottomSheet"

        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private const val MAP_VIEW_BUNDLE_KEY = "map_view_bundle"
        private const val STATE_SELECTED_LATITUDE = "state_selected_latitude"
        private const val STATE_SELECTED_LONGITUDE = "state_selected_longitude"
        private const val STATE_MAP_TYPE_INDEX = "state_map_type_index"
        private val DEFAULT_LATLNG = LatLng(9.93333, -84.08333)
        private const val DEFAULT_ZOOM = 16f

        fun newInstance(latitud: Double?, longitud: Double?): MapCoordinatePickerBottomSheet {
            val fragment = MapCoordinatePickerBottomSheet()
            fragment.arguments = bundleOf(
                ARG_LATITUDE to (latitud ?: Double.NaN),
                ARG_LONGITUDE to (longitud ?: Double.NaN)
            )
            return fragment
        }
    }
}
