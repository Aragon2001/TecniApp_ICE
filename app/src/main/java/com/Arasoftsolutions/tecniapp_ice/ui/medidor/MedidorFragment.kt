package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Arasoftsolutions.tecniapp_ice.Database.entities.MedidorEntity
import com.Arasoftsolutions.tecniapp_ice.Database.room.RoomRepository
import com.Arasoftsolutions.tecniapp_ice.Database.sync.FirebaseSyncManager
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMedidorBinding
import kotlinx.coroutines.launch

class MedidorFragment : Fragment() {

    private var _binding: FragmentMedidorBinding? = null
    private val binding get() = _binding!!

    private lateinit var roomRepository: RoomRepository
    private lateinit var firebaseSyncManager: FirebaseSyncManager
    private lateinit var subregionId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedidorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        roomRepository = RoomRepository.getInstance(context)
        firebaseSyncManager = FirebaseSyncManager(context)

        // ⚠️ IMPORTANTE: Obtén esta subregión del usuario logueado (reemplazar esto dinámicamente)
        subregionId = "Guacimo"

        binding.btnConsultar.setOnClickListener {
            val numero = binding.inputMedidor.text.toString().trim()
            if (numero.isNotEmpty()) {
                buscarMedidor(numero)
            } else {
                binding.inputMedidor.error = "Ingrese un número de medidor"
            }
        }

        binding.btnCopiar.setOnClickListener { copyInfoToClipboard() }
        binding.btnCompartir.setOnClickListener { shareInfo() }
    }

    private fun buscarMedidor(medidorNumber: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // 1. Buscar en ROOM
            val local = roomRepository.buscarMedidorPorNumero(medidorNumber)

            if (local != null) {
                mostrarMedidor(local)
                return@launch
            }

            // 2. Buscar en Firebase
            val remoto = firebaseSyncManager.buscarMedidorEnFirebase(subregionId, medidorNumber)
            if (remoto != null) {
                // Guardar en Room
                roomRepository.insertarMedidor(remoto)
                mostrarMedidor(remoto)
            } else {
                // 3. No encontrado → sugerir registro
                binding.progressBar.visibility = View.GONE
                mostrarDialogoRegistrar(medidorNumber)
            }
        }
    }

    private fun mostrarMedidor(medidor: MedidorEntity) {
        binding.txtCliente.text = medidor.cliente ?: "N/D"
        binding.txtCalle.text = medidor.calle ?: "N/D"
        binding.txtPoste.text = medidor.poste ?: "N/D"
        binding.txtMetros.text = medidor.metros ?: "N/D"
        binding.txtPueblo.text = medidor.pueblo ?: "N/D"
        binding.txtLocation.text = (medidor.localizacion ?: "N/D").toString()

        binding.progressBar.visibility = View.GONE
    }

    private fun mostrarDialogoRegistrar(medidorNumber: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Medidor no encontrado")
            .setMessage("El medidor $medidorNumber no fue encontrado.\n¿Desea registrarlo manualmente?")
            .setPositiveButton("Registrar") { _, _ ->
                // TODO: Navegar a fragmento de registro de medidor
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun copyInfoToClipboard() {
        val info = """
            Cliente: ${binding.txtCliente.text}
            Calle: ${binding.txtCalle.text}
            Poste: ${binding.txtPoste.text}
            Metros: ${binding.txtMetros.text}
            Pueblo: ${binding.txtPueblo.text}
            Localización: ${binding.txtLocation.text}
        """.trimIndent()

        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Medidor", info))
    }

    private fun shareInfo() {
        val info = """
            Cliente: ${binding.txtCliente.text}
            Calle: ${binding.txtCalle.text}
            Poste: ${binding.txtPoste.text}
            Metros: ${binding.txtMetros.text}
            Pueblo: ${binding.txtPueblo.text}
            Localización: ${binding.txtLocation.text}
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, info)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir información del medidor"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
