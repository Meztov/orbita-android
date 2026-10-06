package com.example.orbita

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
import java.time.LocalDate

/** Un resumen editable por día, separado por la cuenta de Firebase. */
data class RegistroOrbita(
    val fecha: String,
    val emocion: String = "Calma",
    val energia: Float = 0.5f,
    val intensidad: Float = 0.65f,
    val notas: String = "",
    val zonaHoraria: String = java.time.ZoneId.systemDefault().id
)

enum class EmocionOrbita(val etiqueta: String, val rgb: Int) {
    ALEGRIA("Alegría", 0xFFD477),
    ENTUSIASMO("Entusiasmo", 0xFFAD45),
    ESPERANZA("Esperanza", 0xA6D97A),
    GRATITUD("Gratitud", 0xF5B99A),
    AMOR("Amor", 0xF17AB4),
    TERNURA("Ternura", 0xF5C6DD),
    CALMA("Calma", 0x66DED2),
    PAZ("Paz", 0xBAEADB),
    SATISFACCION("Satisfacción", 0x39B98D),
    ORGULLO("Orgullo", 0xDFA331),
    NOSTALGIA("Nostalgia", 0xB889A8),
    TRISTEZA("Tristeza", 0x789BFF),
    SOLEDAD("Soledad", 0x8196B2),
    DESOLACION("Desolación", 0x55568C),
    DECEPCION("Decepción", 0x91829E),
    MIEDO("Miedo", 0xA278DD),
    ANSIEDAD("Ansiedad", 0xFF918D),
    ENOJO("Enojo", 0xEE5665),
    FRUSTRACION("Frustración", 0xE97843),
    CONFUSION("Confusión", 0xC6B6F2);
    companion object {
        fun de(etiqueta: String) = entries.firstOrNull { it.etiqueta == etiqueta } ?: CALMA
    }
}

data class EstadoNube(
    val registros: Map<String, RegistroOrbita> = emptyMap(),
    val cargado: Boolean = false,
    val desdeCache: Boolean = true,
    val pendientes: Boolean = false,
    val error: String? = null
)

class RepositorioOrbita(private val uid: String) {
    private val coleccion = FirebaseFirestore.getInstance()
        .collection("usuarios").document(uid).collection("registros")

    fun escuchar(callback: (EstadoNube) -> Unit): ListenerRegistration =
        coleccion.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                callback(EstadoNube(error = "No se pudo sincronizar. Revisa conexión y permisos de Firebase."))
            } else if (snapshot != null) {
                val registros = snapshot.documents.mapNotNull { doc ->
                    val fecha = doc.getString("fecha") ?: return@mapNotNull null
                    if (runCatching { LocalDate.parse(fecha) }.isFailure || doc.id != fecha) {
                        return@mapNotNull null
                    }
                    fecha to RegistroOrbita(
                        fecha = fecha,
                        emocion = doc.getString("emocion") ?: "Calma",
                        energia = (doc.getDouble("energia") ?: 0.5).toFloat().coerceIn(0f, 1f),
                        intensidad = (doc.getDouble("intensidad") ?: 0.65).toFloat().coerceIn(0f, 1f),
                        notas = doc.getString("notas") ?: "",
                        zonaHoraria = doc.getString("zonaHoraria") ?: "America/Mexico_City"
                    )
                }.toMap()
                callback(EstadoNube(registros, true, snapshot.metadata.isFromCache,
                    snapshot.metadata.hasPendingWrites()))
            }
        }

    fun guardar(registro: RegistroOrbita, resultado: (Exception?) -> Unit) {
        require(registro.notas.length <= 10000)
        coleccion.document(registro.fecha).set(mapOf(
            "fecha" to registro.fecha,
            "emocion" to registro.emocion,
            "energia" to registro.energia.toDouble(),
            "intensidad" to registro.intensidad.toDouble(),
            "notas" to registro.notas,
            "zonaHoraria" to registro.zonaHoraria,
            "actualizadoEn" to FieldValue.serverTimestamp()
        )).addOnSuccessListener { resultado(null) }
            .addOnFailureListener { resultado(it) }
    }
}

/** Conserva también lo que aún no has enviado, en ESTE dispositivo y por cuenta. */
class BorradoresOrbita(context: Context, uid: String) {
    private val preferencias = context.getSharedPreferences("orbita_borradores_$uid", Context.MODE_PRIVATE)
    fun leer(fecha: String): RegistroOrbita? = runCatching {
        val texto = preferencias.getString(fecha, null) ?: return null
        val json = JSONObject(texto)
        RegistroOrbita(fecha, json.getString("emocion"), json.getDouble("energia").toFloat(),
            json.getDouble("intensidad").toFloat(), json.getString("notas"), json.getString("zonaHoraria"))
    }.getOrNull()
    fun guardar(r: RegistroOrbita) {
        val json = JSONObject().put("emocion", r.emocion).put("energia", r.energia)
            .put("intensidad", r.intensidad).put("notas", r.notas).put("zonaHoraria", r.zonaHoraria)
        preferencias.edit().putString(r.fecha, json.toString()).apply()
    }
    fun borrarSiCoincide(r: RegistroOrbita) {
        if (leer(r.fecha) == r) preferencias.edit().remove(r.fecha).apply()
    }
}
