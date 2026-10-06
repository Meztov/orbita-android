package com.example.orbita

import kotlin.math.sqrt

/** Intersección rayo/esfera: selecciona el cuerpo visible más cercano, en unidades del mundo. */
internal fun seleccionarPorRayo(origen: FloatArray, direccion: FloatArray, cuerpos: List<CuerpoOrbita>): String? {
    val longitud = sqrt(direccion.sumOf { (it * it).toDouble() }).toFloat()
    if (longitud < 0.000001f) return null
    val dx = direccion[0] / longitud; val dy = direccion[1] / longitud; val dz = direccion[2] / longitud
    var menor = Float.POSITIVE_INFINITY
    var fecha: String? = null
    for (c in cuerpos) {
        if (c.fecha == null) continue
        val ox = origen[0] - c.x; val oy = origen[1] - c.y; val oz = origen[2] - c.z
        val b = ox * dx + oy * dy + oz * dz
        val discriminante = b * b - (ox * ox + oy * oy + oz * oz - c.radio * c.radio)
        if (discriminante < 0f) continue
        val raiz = sqrt(discriminante)
        val entrada = -b - raiz
        val salida = -b + raiz
        val distancia = if (entrada >= 0f) entrada else salida
        if (distancia >= 0f && distancia < menor) { menor = distancia; fecha = c.fecha }
    }
    return fecha
}
