import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.util.Log

class LocalizacionViewModel : ViewModel() {


    // LiveData para los pueblos
    private val _pueblos = MutableLiveData<List<String>>()
    val pueblos: LiveData<List<String>> get() = _pueblos

    // LiveData para las calles
    private val _calles = MutableLiveData<List<String>>()
    val calles: LiveData<List<String>> get() = _calles

    // LiveData para manejar el estado de carga
    private val _estado = MutableLiveData<Estado>()
    val estado: LiveData<Estado> get() = _estado

    // LiveData para manejar la localización
    private val _localizacion = MutableLiveData<Localizacion>()
    val localizacion: LiveData<Localizacion> get() = _localizacion

    private val referenciaDB = FirebaseDatabase.getInstance("https://tecniapp-ice.firebaseio.com/")
        .getReference("Localizaciones")
     private val PueblosDB = FirebaseDatabase.getInstance("https://tecniapp-ice.firebaseio.com/")
        .getReference("pueblos")

    // Método para cargar los pueblos
    fun cargarPueblos() {
        _estado.value = Estado.Cargando
        PueblosDB.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val listaPueblos = mutableListOf("Seleccione un pueblo")
                for (data in snapshot.children) {
                    val codigoPueblo = data.key
                    val nombrePueblo = data.child("nombre").getValue(String::class.java)
                    if (codigoPueblo != null && nombrePueblo != null) {
                        listaPueblos.add("$codigoPueblo - $nombrePueblo")
                    }
                }
                _pueblos.value = listaPueblos
                _estado.value = Estado.Exito
            }

            override fun onCancelled(error: DatabaseError) {
                _estado.value = Estado.Error("Error al cargar pueblos: ${error.message}")
                Log.e("FirebaseErrorPueblos", "Error al cargar pueblos: ${error.message}")
            }
        })
    }

    // Método para cargar las calles basadas en el pueblo seleccionado
    fun cargarCallesParaPueblo(pueblo: Int) {
        _estado.value = Estado.Cargando

        referenciaDB.orderByChild("Pueblo")
            .equalTo(pueblo.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val listaCalles = mutableListOf<String>("Seleccione una calle")
                    for (data in snapshot.children) {
                        val calleLong = data.child("Calle").getValue(Long::class.java)
                        val direccion = data.child("Dirección").getValue(String::class.java)

                        if (calleLong != null && direccion != null) {
                            listaCalles.add("$calleLong - $direccion")
                        }
                    }

                    if (listaCalles.isEmpty()) {
                        _estado.value = Estado.Error("No hay calles disponibles para mostrar.")
                    } else {
                        _calles.value = listaCalles
                        _estado.value = Estado.Exito
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _estado.value = Estado.Error("Error al cargar calles: ${error.message}")
                    Log.e("FirebaseErrorCalles", "Error al cargar calles: ${error.message}")
                }
            })
    }



    // Método para cargar la localización basada en la calle seleccionada
    fun cargarLocalizacionParaCalle(calle: Int, codigoPueblo: Int, direccion: String?) {
        _estado.value = Estado.Cargando

        referenciaDB
            .orderByChild("Pueblo")
            .equalTo(codigoPueblo.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var encontrado = false

                    for (data in snapshot.children) {
                        val calleValor = data.child("Calle").getValue(Double::class.java)
                        val direccionValor = data.child("Dirección").getValue(String::class.java)

                        if (calleValor?.toInt() == calle && direccionValor == direccion) {
                            val latitud = data.child("Latitud").getValue(Double::class.java)
                            val longitud = data.child("Longitud").getValue(Double::class.java)
                            val delPoste = data.child("del poste ").getValue(Double::class.java)?.toInt()
                            val alPoste = data.child("al poste").getValue(Double::class.java)?.toInt()

                            val localizacion = Localizacion(
                                direccion = direccion ?: "Sin dirección",
                                latitud = latitud ?: 0.0,
                                longitud = longitud ?: 0.0,
                                delPoste = delPoste ?: 0,
                                alPoste = alPoste ?: 0,
                                calleValor = calle ?:0
                            )

                            _localizacion.value = localizacion
                            encontrado = true
                            break
                        }
                    }

                    if (!encontrado) {
                        _estado.value = Estado.Error("No se encontró la localización para la calle seleccionada en el pueblo.")
                    } else {
                        _estado.value = Estado.Exito
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _estado.value = Estado.Error("Error al cargar la localización: ${error.message}")
                    Log.e("FirebaseErrorLocalizacion", "Error al cargar la localización: ${error.message}")
                }
            })
    }

    // Clase de datos para la localización
    data class Localizacion(
        val direccion: String,
        val latitud: Double,
        val longitud: Double,
        val delPoste: Int,
        val alPoste: Int,
        val calleValor: Int
    )

    // Clase sellada para representar el estado de la carga
    sealed class Estado {
        object Cargando : Estado()
        object Exito : Estado()
        data class Error(val mensaje: String) : Estado()
    }
}
