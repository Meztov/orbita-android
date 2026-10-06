package com.example.orbita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class InicioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { abrirLogin(); return }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFBCA7FF), secondary = Color(0xFF66DED2),
                background = Color(0xFF070A18), surface = Color(0xFF15182B)
            )) { PantallaOrbita(uid) }
        }
    }
    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser == null) abrirLogin()
    }
    private fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

@Composable
private fun PantallaOrbita(uid: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember(uid) { RepositorioOrbita(uid) }
    val borradores = remember(uid) { BorradoresOrbita(context.applicationContext, uid) }
    var nube by remember(uid) { mutableStateOf(EstadoNube()) }
    var hoy by remember { mutableStateOf(LocalDate.now()) }
    var fechaTexto by rememberSaveable(uid) { mutableStateOf(hoy.toString()) }
    val fecha = LocalDate.parse(fechaTexto)
    var editor by remember(uid, fechaTexto) {
        mutableStateOf(borradores.leer(fechaTexto) ?: nube.registros[fechaTexto] ?: RegistroOrbita(fechaTexto))
    }
    var modificado by remember(uid, fechaTexto) { mutableStateOf(borradores.leer(fechaTexto) != null) }
    var enviado by remember(uid, fechaTexto) { mutableStateOf<RegistroOrbita?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var reintento by remember { mutableIntStateOf(0) }
    var vistaAmplia by rememberSaveable { mutableStateOf(false) }
    var mostrarPaleta by rememberSaveable { mutableStateOf(false) }
    var volverAlUniverso by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !vistaAmplia && volverAlUniverso && !mostrarPaleta) {
        vistaAmplia = true; volverAlUniverso = false
    }

    DisposableEffect(repo, reintento) {
        val listener = repo.escuchar { nuevo ->
            nube = if (nuevo.error != null) nube.copy(error = nuevo.error) else nuevo
        }
        onDispose { listener.remove() }
    }
    // Se actualiza al cruzar medianoche, manteniendo el día que estabas editando.
    LaunchedEffect(Unit) { while (true) { hoy = LocalDate.now(); delay(30000) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hoy = LocalDate.now()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(nube.registros[fechaTexto], fechaTexto) {
        if (!modificado) editor = nube.registros[fechaTexto] ?: RegistroOrbita(fechaTexto)
    }
    fun editar(r: RegistroOrbita) {
        editor = r
        modificado = true
        borradores.guardar(r)
        mensaje = null
    }
    val emocion = EmocionOrbita.de(editor.emocion)
    val acento = Color(0xFF000000L or emocion.rgb.toLong())
    val inicio = nube.registros.keys.minOrNull()?.let(LocalDate::parse)
    // No inferir ausencias en un dispositivo nuevo antes de obtener datos del servidor.
    val ausenciasConfirmadas = nube.cargado && !nube.desdeCache && nube.error == null
    val escena = remember(editor, nube.registros, hoy, vistaAmplia, modificado, ausenciasConfirmadas) {
        crearEscena(editor, nube.registros, hoy, inicio, vistaAmplia, modificado, ausenciasConfirmadas)
    }
    val estado = when {
        nube.error != null -> nube.error!!
        nube.pendientes -> "Guardado en este celular · pendiente de sincronizar"
        !nube.cargado -> "Buscando tus planetas…"
        nube.desdeCache -> "Datos de este celular · esperando sincronización"
        else -> "Tu universo está sincronizado"
    }
    val puedeGuardar = nube.cargado && nube.error == null && !fecha.isAfter(hoy) &&
        editor != enviado && (modificado || !nube.registros.containsKey(fechaTexto))

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF070A18), Color(0xFF17112E), Color(0xFF080B19)
    ))).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ó R B I T A", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Color.White)
                Text("Tu universo emocional", fontSize = 12.sp, color = Color(0xFFA5AAC4))
            }
            TextButton(onClick = { vistaAmplia = !vistaAmplia; volverAlUniverso = false }) {
                Text(if (vistaAmplia) "Mi esfera" else "Universo")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { fechaTexto = fecha.minusDays(1).toString(); mensaje = null }) { Text("‹", fontSize = 28.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fecha.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "MX"))), color = Color.White)
                Text(if (fecha == hoy) "HOY" else fecha.year.toString(), color = Color(0xFF9498B6), fontSize = 10.sp)
            }
            TextButton(enabled = fecha.isBefore(hoy), onClick = {
                fechaTexto = fecha.plusDays(1).toString(); mensaje = null
            }) { Text("›", fontSize = 28.sp) }
        }
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Universo3D(escena, Modifier.matchParentSize()) { elegida ->
                if (vistaAmplia) {
                    fechaTexto = elegida
                    mensaje = null
                    vistaAmplia = false
                    volverAlUniverso = true
                }
            }
            Text(if (vistaAmplia) "Toca un planeta · arrastra para explorar" else "Así se siente tu día",
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp), color = Color(0xFFD0CAE4), fontSize = 12.sp)
            Text(if (vistaAmplia) "Planetas: días registrados · meteoritos: días sin registro" else
                "${editor.emocion}  ·  energía ${(editor.energia * 100).toInt()}%  ·  intensidad ${(editor.intensidad * 100).toInt()}%",
                Modifier.align(Alignment.BottomCenter).padding(8.dp), color = Color(0xFFA5AAC4), fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().background(Color(0xCC15172A), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF2B2C48), RoundedCornerShape(24.dp)).padding(18.dp)) {
            if (ausenciasConfirmadas && inicio != null && !fecha.isBefore(inicio) &&
                fecha.isBefore(hoy) && !nube.registros.containsKey(fechaTexto) && !modificado) {
                Text("Este día no tiene registro. Puedes completarlo aquí.",
                    color = Color(0xFFB1B4CE), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            OutlinedButton(onClick = { mostrarPaleta = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) {
                Box(Modifier.size(24.dp).background(acento, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text("${editor.emocion} · Cambiar emoción", color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            ControlEsfera("Energía a lo largo del día", "Tamaño", editor.energia, acento) { editar(editor.copy(energia = it)) }
            ControlEsfera("Intensidad de la emoción", "Opacidad", editor.intensidad, acento) { editar(editor.copy(intensidad = it)) }
            OutlinedTextField(value = editor.notas, onValueChange = {
                if (it.length <= 10000) editar(editor.copy(notas = it))
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3, maxLines = 7,
                label = { Text("Notas de tu día") }, placeholder = { Text("Lo que quieras recordar…") },
                shape = RoundedCornerShape(16.dp), supportingText = {
                    if (editor.notas.length > 9500) Text("${editor.notas.length}/10000")
                })
            Button(onClick = {
                val copia = editor.copy(zonaHoraria = java.time.ZoneId.systemDefault().id)
                editor = copia
                borradores.guardar(copia)
                enviado = copia
                mensaje = "Guardando · si no hay conexión, se enviará al reconectar"
                repo.guardar(copia) { error ->
                    if (error == null) {
                        borradores.borrarSiCoincide(copia)
                        if (editor == copia) modificado = false
                        mensaje = "Registro respaldado en Firebase"
                    } else {
                        enviado = null
                        mensaje = "No se pudo respaldar. Tu borrador sigue en este celular; vuelve a guardar."
                    }
                }
            }, enabled = puedeGuardar, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = acento, contentColor = Color(0xFF111226))) {
                Text(if (nube.registros.containsKey(fechaTexto)) "Actualizar mi planeta" else "Guardar mi planeta",
                    fontWeight = FontWeight.SemiBold)
            }
            Text(mensaje ?: if (modificado) "Borrador en este celular · pulsa Guardar para respaldarlo" else estado,
                Modifier.padding(top = 12.dp), color = Color(0xFFB1B4CE), fontSize = 11.sp)
            if (nube.error != null) TextButton(onClick = { reintento++ }) { Text("Reintentar sincronización") }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (mostrarPaleta) {
        AlertDialog(onDismissRequest = { mostrarPaleta = false },
            title = { Text("¿Qué emoción coloreó tu día?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmocionOrbita.entries.toList().chunked(2).forEach { fila ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { e ->
                                val color = Color(0xFF000000L or e.rgb.toLong())
                                val activa = e == emocion
                                Row(Modifier.weight(1f).background(if (activa) Color(0xFF35304C) else Color(0xFF202137),
                                    RoundedCornerShape(12.dp))
                                    .border(1.dp, if (activa) color else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { editar(editor.copy(emocion = e.etiqueta)); mostrarPaleta = false }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).background(color, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(e.etiqueta, fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mostrarPaleta = false }) { Text("Cerrar") } })
    }

}

@Composable
private fun ControlEsfera(titulo: String, significado: String, valor: Float, color: Color, cambiar: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, color = Color(0xFFD6D3E6), fontSize = 12.sp)
        Text("$significado · ${(valor * 100).toInt()}%", color = Color(0xFFA5AAC4), fontSize = 10.sp)
    }
    Slider(value = valor, onValueChange = cambiar, colors = SliderDefaults.colors(
        thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color(0xFF33334D)))
}

@Composable
private fun Universo3D(escena: EscenaOrbita, modifier: Modifier, alSeleccionar: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    val seleccionActual by rememberUpdatedState(alSeleccionar)
    val vista = remember(context) { VistaUniverso(context) }
    AndroidView(factory = { vista }, modifier = modifier, update = { it.alSeleccionar = { fecha -> seleccionActual(fecha) }; it.actualizar(escena) })
    DisposableEffect(vista, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vista.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); vista.onPause() }
    }
}
