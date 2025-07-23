package com.Arasoftsolutions.tecniapp_ice.ui.medidor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.Arasoftsolutions.tecniapp_ice.databinding.FragmentMedidorBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MedidorFragment : Fragment() {

    private var _binding: FragmentMedidorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedidorBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.button.setOnClickListener {
            val medidorNumber = binding.editTextNumber.text.toString()
            if (medidorNumber.isNotEmpty()) {
                binding.progressBar.visibility = View.VISIBLE // Muestra el ProgressBar
                binding.editTextNumber.error = null // Limpia el mensaje de error
                consultarMedidor(medidorNumber)
            } else {
                binding.editTextNumber.error = "Por favor, ingrese un número de medidor"
            }
        }

        // Evento para el botón Copiar
        binding.btnCopy.setOnClickListener {
            copyInfoToClipboard()
        }

        // Evento para el botón Compartir
        binding.btnShare.setOnClickListener {
            shareInfo()
        }

        return root
    }

    private fun consultarMedidor(medidorNumber: String) {
        val database = FirebaseDatabase.getInstance("https://tecniapp-ice-default-rtdb.firebaseio.com/")
        val ref = database.getReference("Medidores/Medidores/SubRegion Guapiles")

        // Acceder a la ruta correcta, usando el medidorNumber como clave
        ref.child(medidorNumber).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.progressBar.visibility = View.GONE // Oculta el ProgressBar
                if (snapshot.exists()) {
                    // Obtener los valores directamente desde la clave del medidor
                    val cliente = snapshot.child("Cliente").getValue(String::class.java)
                    val localizacion = snapshot.child("Localización").getValue(Long::class.java)?.toString()
                    val pueblo = snapshot.child("Pueblo").getValue(String::class.java)
                    val calle = snapshot.child("Calle").getValue(String::class.java)
                    val poste = snapshot.child("Poste").getValue(String::class.java)
                    val metros = snapshot.child("Metros").getValue(String::class.java)

                    // Actualizar la UI con los datos obtenidos
                    binding.txtCliente.text = cliente ?: "No disponible"
                    binding.txtLocation.text = localizacion ?: "No disponible"
                    binding.txtPueblo.text = pueblo ?: "No disponible"
                    binding.txtCalle.text = calle ?: "No disponible"
                    binding.txtPoste.text = poste ?: "No disponible"
                    binding.txtMetros.text = metros ?: "No disponible"
                } else {
                    binding.editTextNumber.error = "Medidor no encontrado"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE // Oculta el ProgressBar en caso de error
                binding.editTextNumber.error = "Error al consultar la base de datos"
            }
        })
    }


    private fun copyInfoToClipboard() {
        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        val textToCopy = """
            ${binding.txtCliente.text}
            ${binding.txtLocation.text}
            ${binding.txtPueblo.text}
            ${binding.txtCalle.text}
            ${binding.txtPoste.text}
            ${binding.txtMetros.text}
        """.trimIndent()

        val clip = ClipData.newPlainText("Medidor Info", textToCopy)
        clipboard?.setPrimaryClip(clip)
    }

    private fun shareInfo() {
        val textToShare = """
            ${binding.txtCliente.text}
            ${binding.txtLocation.text}
            ${binding.txtPueblo.text}
            ${binding.txtCalle.text}
            ${binding.txtPoste.text}
            ${binding.txtMetros.text}
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir información"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
