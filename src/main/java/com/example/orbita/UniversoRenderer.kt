package com.example.orbita

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.time.LocalDate
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

data class CuerpoOrbita(
    val x: Float, val y: Float, val z: Float, val radio: Float,
    val rgb: Int, val alfa: Float, val meteorito: Boolean = false, val fecha: String? = null
)
data class EscenaOrbita(val cuerpos: List<CuerpoOrbita>, val amplia: Boolean)

fun crearEscena(
    editor: RegistroOrbita, registros: Map<String, RegistroOrbita>, hoy: LocalDate,
    inicio: LocalDate?, amplia: Boolean, editado: Boolean, ausenciasConfirmadas: Boolean
): EscenaOrbita {
    val seleccionado = LocalDate.parse(editor.fecha)
    fun cuerpo(r: RegistroOrbita, x: Float, y: Float, z: Float, grande: Boolean): CuerpoOrbita =
        CuerpoOrbita(x, y, z, if (grande) 0.6f + 1.15f * r.energia else 0.12f + 0.32f * r.energia,
            EmocionOrbita.de(r.emocion).rgb, r.intensidad.coerceIn(0.05f, 1f), fecha = r.fecha)
    val cuerpos = mutableListOf<CuerpoOrbita>()
    val faltaSeleccionado = seleccionado.isBefore(hoy) && inicio != null &&
        !seleccionado.isBefore(inicio) && !registros.containsKey(editor.fecha) && !editado && ausenciasConfirmadas
    if (!amplia) {
        cuerpos += if (faltaSeleccionado) CuerpoOrbita(0f, 0f, 0f, 1f, 0x9C8E85, 1f, true, editor.fecha)
            else cuerpo(editor, 0f, 0f, 0f, true)
    }
    val primerDia = seleccionado.withDayOfMonth(1)
    for (dia in 1..seleccionado.lengthOfMonth()) {
        val fecha = primerDia.withDayOfMonth(dia)
        if (fecha.isAfter(hoy)) continue
        if (!amplia && fecha == seleccionado) continue
        val registro = if (fecha == seleccionado && (editado || fecha == hoy)) editor else registros[fecha.toString()]
        val falta = registro == null && fecha.isBefore(hoy) && inicio != null &&
            !fecha.isBefore(inicio) && ausenciasConfirmadas
        if (registro == null && !falta) continue
        val angulo = dia * 2.3999632297
        val distancia = if (amplia) 1.4 + sqrt(dia.toDouble()) * 0.65 else 2.2 + sqrt(dia.toDouble()) * 0.32
        val x = (cos(angulo) * distancia).toFloat()
        val y = (sin(angulo) * distancia * 0.72).toFloat()
        val z = if (amplia) (sin(dia * 0.8) * 1.5).toFloat() else (-3.8 + sin(dia * 0.8)).toFloat()
        cuerpos += if (registro != null) cuerpo(registro, x, y, z, false)
            else CuerpoOrbita(x, y, z, 0.22f, 0x9C8E85, 1f, true, fecha.toString())
    }
    return EscenaOrbita(cuerpos, amplia)
}

class VistaUniverso(context: Context) : GLSurfaceView(context) {
    private val universo = RendererUniverso()
    var alSeleccionar: (String) -> Unit = {}
    private val umbral = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var inicioX = 0f
    private var inicioY = 0f
    private var arrastrando = false
    private var anteriorX = 0f
    private var anteriorY = 0f
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(universo)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Universo emocional tridimensional. Arrastra para rotarlo."
        isClickable = true
    }
    fun actualizar(escena: EscenaOrbita) { universo.escena = escena; requestRender() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anteriorX = event.x; anteriorY = event.y
                inicioX = event.x; inicioY = event.y; arrastrando = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!arrastrando && hypot(event.x - inicioX, event.y - inicioY) > umbral) arrastrando = true
                if (!arrastrando) return true
                universo.giro += (event.x - anteriorX) * 0.28f
                universo.inclinacion = (universo.inclinacion + (event.y - anteriorY) * 0.18f).coerceIn(-50f, 50f)
                anteriorX = event.x; anteriorY = event.y
                requestRender()
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!arrastrando && hypot(event.x - inicioX, event.y - inicioY) <= umbral) {
                    val x = event.x; val y = event.y
                    queueEvent {
                        val fecha = universo.seleccionar(x, y)
                        if (fecha != null) post { alSeleccionar(fecha) }
                    }
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

private data class FotogramaSeleccion(val inversa: FloatArray, val cuerpos: List<CuerpoOrbita>, val ancho: Int, val alto: Int)

private data class Malla(val datos: FloatBuffer, val cantidad: Int, val modo: Int = GLES20.GL_TRIANGLES)

private class RendererUniverso : GLSurfaceView.Renderer {
    @Volatile var escena = EscenaOrbita(emptyList(), false)
    @Volatile var giro = 0f
    @Volatile var inclinacion = 0f
    private var programa = 0
    private var posicion = 0
    private var normal = 0
    private var locMvp = 0
    private var locModelo = 0
    private var locColor = 0
    private var locModo = 0
    private var locBrillo = 0
    private var ancho = 0
    private var alto = 0
    private var seleccion: FotogramaSeleccion? = null
    private val proyeccion = FloatArray(16)
    private val vista = FloatArray(16)
    private val mundo = FloatArray(16)
    private val modelo = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val esfera = crearEsfera(28, 48, false)
    private val roca = crearEsfera(8, 12, true)
    private val estrellas = crearEstrellas()
    private val orbita = crearAnillo()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.027f, 0.033f, 0.078f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        programa = GLES20.glCreateProgram()
        GLES20.glAttachShader(programa, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(programa, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(programa)
        val estado = IntArray(1)
        GLES20.glGetProgramiv(programa, GLES20.GL_LINK_STATUS, estado, 0)
        check(estado[0] != 0) { GLES20.glGetProgramInfoLog(programa) }
        posicion = GLES20.glGetAttribLocation(programa, "aPosition")
        normal = GLES20.glGetAttribLocation(programa, "aNormal")
        locMvp = GLES20.glGetUniformLocation(programa, "uMvp")
        locModelo = GLES20.glGetUniformLocation(programa, "uModelView")
        locColor = GLES20.glGetUniformLocation(programa, "uColor")
        locModo = GLES20.glGetUniformLocation(programa, "uMode")
        locBrillo = GLES20.glGetUniformLocation(programa, "uGlow")
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        ancho = width; alto = height
        seleccion = null
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proyeccion, 0, 46f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 100f)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programa)
        val actual = escena
        Matrix.setLookAtM(vista, 0, 0f, 0f, if (actual.amplia) 14f else 6f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(mundo, 0)
        Matrix.rotateM(mundo, 0, inclinacion, 1f, 0f, 0f)
        Matrix.rotateM(mundo, 0, giro, 0f, 1f, 0f)
        Matrix.setIdentityM(modelo, 0)
        dibujar(estrellas, modelo, 0xCFD5FF, 0.85f, 1f)
        // Nebulosas: halos volumétricos tenues detrás de los planetas.
        GLES20.glDepthMask(false)
        listOf(CuerpoOrbita(-9f, 4f, -25f, 12f, 0x553478, 0.07f),
            CuerpoOrbita(11f, -6f, -24f, 10f, 0x246B82, 0.055f)).forEach {
            prepararModelo(it, rotar = false)
            dibujar(esfera, modelo, it.rgb, it.alfa, 0f, 1f)
        }
        GLES20.glDepthMask(true)
        val opacos = actual.cuerpos.filter { it.alfa >= 0.999f }
        opacos.forEach { prepararModelo(it); dibujar(if (it.meteorito) roca else esfera, modelo, it.rgb, it.alfa) }
        // Transparencias de atrás hacia delante en coordenadas de cámara.
        GLES20.glDepthMask(false)
        actual.cuerpos.filter { it.alfa < 0.999f }.sortedBy {
            val p = FloatArray(4)
            Matrix.multiplyMV(p, 0, mundo, 0, floatArrayOf(it.x, it.y, it.z, 1f), 0)
            p[2]
        }.forEach { prepararModelo(it); dibujar(esfera, modelo, it.rgb, it.alfa) }
        actual.cuerpos.forEach {
            if (!it.meteorito) {
                prepararModelo(it.copy(radio = it.radio * 1.08f))
                dibujar(esfera, modelo, it.rgb, 0.12f * it.alfa, 0f, 1f)
                if (!actual.amplia && it.x == 0f && it.y == 0f) {
                    prepararModelo(it.copy(radio = it.radio * 1.32f))
                    Matrix.rotateM(modelo, 0, 67f, 1f, 0f, 0f)
                    dibujar(orbita, modelo, it.rgb, 0.3f, 2f)
                }
            }
        }
        GLES20.glDepthMask(true)
        val vm = FloatArray(16)
        val pvm = FloatArray(16)
        val inversa = FloatArray(16)
        Matrix.multiplyMM(vm, 0, vista, 0, mundo, 0)
        Matrix.multiplyMM(pvm, 0, proyeccion, 0, vm, 0)
        seleccion = if (Matrix.invertM(inversa, 0, pvm, 0))
            FotogramaSeleccion(inversa, actual.cuerpos, ancho, alto) else null
    }
    fun seleccionar(x: Float, y: Float): String? {
        val frame = seleccion ?: return null
        if (frame.ancho <= 0 || frame.alto <= 0) return null
        val nx = 2f * x / frame.ancho - 1f
        val ny = 1f - 2f * y / frame.alto
        fun desproyectar(z: Float): FloatArray? {
            val p = FloatArray(4)
            Matrix.multiplyMV(p, 0, frame.inversa, 0, floatArrayOf(nx, ny, z, 1f), 0)
            if (abs(p[3]) < 0.000001f) return null
            return floatArrayOf(p[0] / p[3], p[1] / p[3], p[2] / p[3])
        }
        val cerca = desproyectar(-1f) ?: return null
        val lejos = desproyectar(1f) ?: return null
        return seleccionarPorRayo(cerca, floatArrayOf(lejos[0] - cerca[0], lejos[1] - cerca[1], lejos[2] - cerca[2]), frame.cuerpos)
    }
    private fun prepararModelo(c: CuerpoOrbita, rotar: Boolean = true) {
        if (rotar) System.arraycopy(mundo, 0, modelo, 0, 16) else Matrix.setIdentityM(modelo, 0)
        Matrix.translateM(modelo, 0, c.x, c.y, c.z)
        Matrix.scaleM(modelo, 0, c.radio, c.radio, c.radio)
    }
    private fun dibujar(malla: Malla, model: FloatArray, rgb: Int, alfa: Float, modo: Float = 0f, brillo: Float = 0f) {
        Matrix.multiplyMM(mv, 0, vista, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proyeccion, 0, mv, 0)
        GLES20.glUniformMatrix4fv(locMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(locModelo, 1, false, mv, 0)
        GLES20.glUniform4f(locColor, ((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f,
            (rgb and 255) / 255f, alfa)
        GLES20.glUniform1f(locModo, modo)
        GLES20.glUniform1f(locBrillo, brillo)
        malla.datos.position(0)
        GLES20.glVertexAttribPointer(posicion, 3, GLES20.GL_FLOAT, false, 24, malla.datos)
        malla.datos.position(3)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, malla.datos)
        GLES20.glEnableVertexAttribArray(posicion)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glDrawArrays(malla.modo, 0, malla.cantidad)
    }
    private fun shader(tipo: Int, codigo: String): Int {
        val id = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(id, codigo)
        GLES20.glCompileShader(id)
        val estado = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, estado, 0)
        check(estado[0] != 0) { GLES20.glGetShaderInfoLog(id) }
        return id
    }
    companion object {
        private val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModelView;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec3 vLocal;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = mat3(uModelView) * aNormal;
                vPosition = (uModelView * vec4(aPosition, 1.0)).xyz;
                vLocal = aPosition;
                gl_PointSize = 2.5;
            }
        """.trimIndent()
        private val FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            uniform float uMode;
            uniform float uGlow;
            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec3 vLocal;
            void main() {
                if (uMode > 1.5) { gl_FragColor = uColor; return; }
                if (uMode > 0.5) {
                    float d = length(gl_PointCoord - vec2(0.5));
                    gl_FragColor = vec4(uColor.rgb, uColor.a * (1.0 - smoothstep(0.1, 0.5, d)));
                    return;
                }
                vec3 n = normalize(vNormal);
                vec3 eye = normalize(-vPosition);
                vec3 light = normalize(vec3(-0.55, 0.8, 1.2));
                float diffuse = max(dot(n, light), 0.0);
                float rim = pow(1.0 - max(dot(n, eye), 0.0), 2.4);
                float shine = pow(max(dot(reflect(-light, n), eye), 0.0), 38.0);
                float bands = sin(vLocal.y * 14.0 + sin(vLocal.x * 8.0) * 0.7) * 0.035;
                vec3 color = uColor.rgb * (0.22 + diffuse * 0.8 + bands);
                color += vec3(0.85, 0.9, 1.0) * shine * 0.4 + uColor.rgb * rim * 0.45;
                if (uGlow > 0.5) {
                    gl_FragColor = vec4(uColor.rgb, uColor.a * rim);
                } else {
                    gl_FragColor = vec4(color, uColor.a);
                }
            }
        """.trimIndent()
    }
}

private fun malla(valores: List<Float>, modo: Int = GLES20.GL_TRIANGLES): Malla {
    val buffer = ByteBuffer.allocateDirect(valores.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    buffer.put(valores.toFloatArray()).position(0)
    return Malla(buffer, valores.size / 6, modo)
}
private fun crearEsfera(filas: Int, columnas: Int, roca: Boolean): Malla {
    val vertices = ArrayList<Float>()
    fun punto(fila: Int, columna: Int): FloatArray {
        val theta = PI * fila / filas
        val phi = 2.0 * PI * columna / columnas
        val x = (sin(theta) * cos(phi)).toFloat()
        val y = cos(theta).toFloat()
        val z = (sin(theta) * sin(phi)).toFloat()
        val r = if (roca) 1f + 0.17f * sin(x * 11f + z * 7f) * cos(y * 13f) else 1f
        return floatArrayOf(x * r, y * r, z * r, x, y, z)
    }
    for (f in 0 until filas) for (c in 0 until columnas) {
        val a = punto(f, c); val b = punto(f + 1, c)
        val d = punto(f, c + 1); val e = punto(f + 1, c + 1)
        listOf(a, d, b, d, e, b).forEach { p -> p.forEach { vertices.add(it) } }
    }
    return malla(vertices)
}
private fun crearEstrellas(): Malla {
    val random = Random(2026)
    val vertices = ArrayList<Float>()
    repeat(420) {
        vertices.add((random.nextFloat() - 0.5f) * 65f)
        vertices.add((random.nextFloat() - 0.5f) * 42f)
        vertices.add(-15f - random.nextFloat() * 25f)
        repeat(3) { vertices.add(0f) }
    }
    return malla(vertices, GLES20.GL_POINTS)
}
private fun crearAnillo(): Malla {
    val vertices = ArrayList<Float>()
    repeat(160) { i ->
        val a = 2 * PI * i / 160
        vertices.add(cos(a).toFloat()); vertices.add(sin(a).toFloat()); vertices.add(0f)
        vertices.add(0f); vertices.add(0f); vertices.add(1f)
    }
    return malla(vertices, GLES20.GL_LINE_LOOP)
}
