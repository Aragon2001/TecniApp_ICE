package com.Arasoftsolutions.tecniapp_ice.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.Arasoftsolutions.tecniapp_ice.R
import com.Arasoftsolutions.tecniapp_ice.databinding.BottomsheetMapPickerBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MapCoordinatePickerBottomSheet : BottomSheetDialogFragment(), OnMapReadyCallback {

    private var _binding: BottomsheetMapPickerBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null

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
        val initialLat = arguments?.getDouble(ARG_LATITUDE)?.takeIf { !it.isNaN() }
        val initialLng = arguments?.getDouble(ARG_LONGITUDE)?.takeIf { !it.isNaN() }
        if (initialLat != null && initialLng != null) {
            selectedLatLng = LatLng(initialLat, initialLng)
        }

        val mapViewBundle = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)
        binding.mapView.onCreate(mapViewBundle)
        binding.mapView.getMapAsync(this)
        actualizarEtiqueta()

        binding.btnCancelar.setOnClickListener { dismiss() }
        binding.btnConfirmar.setOnClickListener {
            val result = selectedLatLng ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_LAT to result.latitude,
                    RESULT_LNG to result.longitude
                )
            )
            dismiss()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true
            val start = selectedLatLng ?: DEFAULT_LATLNG
            moveCamera(CameraUpdateFactory.newLatLngZoom(start, DEFAULT_ZOOM))
            selectedLatLng?.let { addMarker(MarkerOptions().position(it)) }
            setOnMapClickListener { latLng ->
                selectedLatLng = latLng
                clear()
                addMarker(MarkerOptions().position(latLng))
                actualizarEtiqueta()
            }
        }
    }

    private fun actualizarEtiqueta() {
        val coords = selectedLatLng
        binding.tvSelectedCoordinates.text = if (coords == null) {
            binding.btnConfirmar.isEnabled = false
            getString(R.string.map_picker_pending)
        } else {
            binding.btnConfirmar.isEnabled = true
            getString(
                R.string.map_picker_selected,
                coords.latitude,
                coords.longitude
            )
        }
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
        _binding = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        var mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
        if (mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle)
        }
        binding.mapView.onSaveInstanceState(mapViewBundle)
    }

    companion object {
        const val REQUEST_KEY = "map_picker_result"
        const val RESULT_LAT = "result_latitude"
        const val RESULT_LNG = "result_longitude"
        const val TAG = "MapCoordinatePickerBottomSheet"

        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private val DEFAULT_LATLNG = LatLng(9.7489, -83.7534)
        private const val DEFAULT_ZOOM = 13f
        private const val MAP_VIEW_BUNDLE_KEY = "map_view_bundle"

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
