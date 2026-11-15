package com.foncit.telefono.internal

import android.content.Context
import android.util.Log
import com.foncit.telefono.FoncitTelefono
import com.foncit.telefono.LlamadaReciente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** El token ya no sirve (se revocó o el usuario cambió): hay que pedir uno nuevo */
internal class TokenInvalido(mensaje: String? = null) : Exception(mensaje)

/** Cliente de WEBSERVICES/V1/TelefonoMovilWS.php de Foncit (JSON por HTTPS, con el token en Authorization).
 * El token se pide con el usuario de la plataforma, el paquete de la app y las llaves con que está firmada (iniciarUsuario) */
internal object ApiFoncit {

    /** Token del teléfono: el guardado o, si no hay, uno nuevo para el usuario (iniciarUsuario) */
    suspend fun token(forzarNuevo: Boolean = false): String? {
        if (!forzarNuevo) Almacen.token?.let { if (it.isNotBlank()) return it }
        val usuario = FoncitTelefono.usuario
        if (usuario.isBlank()) return null
        val contexto = FoncitTelefono.contexto
        val datos = JSONObject()
            .put("IdExterno", usuario)
            .put("Paquete", contexto.packageName)
            .put("Plataforma", "android")
            .put("Llaves", Firma.llaves(contexto))
        val respuesta = enviar("iniciarUsuario", datos, null).second
        val nuevo = if (respuesta.optBoolean("status")) respuesta.optString("Token").ifBlank { null } else null
        if (nuevo == null) throw TokenInvalido(respuesta.optString("mensaje"))
        Almacen.token = nuevo
        return nuevo
    }

    /** POST a TelefonoMovilWS; regresa el código HTTP y el JSON */
    private suspend fun enviar(solicitud: String, datos: JSONObject, token: String?): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val ruta = Rutas.telefonoMovil(FoncitTelefono.ajustes.ambiente)
        val conexion = URL(ruta).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 10000
            conexion.readTimeout = 20000
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json")
            if (token != null) conexion.setRequestProperty("Authorization", "Bearer $token")
            datos.put("llaveAcceso", Rutas.LLAVE)
            datos.put("solicitud", solicitud)
            datos.put("Idioma", if (Locale.getDefault().language == "en") "en" else "es")
            conexion.outputStream.use { it.write(datos.toString().toByteArray()) }
            val codigo = conexion.responseCode
            val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            /** Para saber a dónde llama y qué contestó el servidor: logcat, filtro FoncitTelefono (solo status y mensaje: no se escribe el token ni las contraseñas) */
            val json = runCatching { JSONObject(texto) }.getOrElse { JSONObject() }
            Log.i("FoncitTelefono", "$solicitud -> $ruta HTTP $codigo status=${json.opt("status")} mensaje=${json.optString("mensaje")}")
            Pair(codigo, json)
        } catch (e: Exception) {
            Log.w("FoncitTelefono", "$solicitud -> $ruta falló: ${e.message}")
            throw e
        } finally {
            conexion.disconnect()
        }
    }

    private suspend fun peticion(solicitud: String, datos: JSONObject = JSONObject(), reintentar: Boolean = true): JSONObject {
        val token = token() ?: throw TokenInvalido()
        val respuesta = enviar(solicitud, datos, token)
        if (respuesta.first == 401) {
            /** Token revocado: se pide uno nuevo una vez */
            Almacen.token = null
            if (reintentar && token(forzarNuevo = true) != null) return peticion(solicitud, datos, false)
            throw TokenInvalido()
        }
        return respuesta.second
    }

    /** Extensiones móviles del usuario (usuario, contraseña, dominio, puerto, TLS), las actuales de Foncit.
     * Se guardan para conectar sin red al despertar; null si Foncit no respondió bien (se usan las guardadas) */
    suspend fun mostrarTelefonos(): JSONArray? {
        val respuesta = peticion("mostrarTelefono")
        /** Aún no le asignan extensiones: lista vacía (no las guardadas) */
        if (respuesta.optInt("SinExtensiones") == 1) {
            Almacen.telefonos = JSONArray()
            return JSONArray()
        }
        if (!respuesta.optBoolean("status")) return null
        val telefonos = respuesta.optJSONArray("rrTelefonos")
            ?: respuesta.optJSONObject("rrTelefono")?.let { JSONArray().put(it) }
            ?: return null
        Almacen.telefonos = telefonos
        return telefonos
    }

    /** Guarda el celular en Foncit (TBL_PBX_DISPOSITIVOS) para que le avise de las llamadas con la app cerrada. Se ve en logcat: FoncitTelefono */
    suspend fun registrarDispositivo(context: Context) {
        val tokenPush = Almacen.tokenPush
        if (tokenPush.isNullOrBlank()) {
            Log.w("FoncitTelefono", "registrarDispositivo: todavía no hay token de avisos (registrarTokenPush)")
            return
        }
        runCatching {
            peticion("registrarDispositivo", JSONObject()
                .put("Plataforma", Almacen.plataformaPush ?: "android")
                .put("TokenPush", tokenPush)
                .put("App", context.packageName))
        }.onSuccess {
            Log.i("FoncitTelefono", "registrarDispositivo: ${it.optBoolean("status")} ${it.optString("mensaje")}")
        }.onFailure {
            Log.w("FoncitTelefono", "registrarDispositivo: ${it.message}", it)
        }
    }

    suspend fun quitarDispositivo() {
        val tokenPush = Almacen.tokenPush ?: return
        runCatching { peticion("quitarDispositivo", JSONObject().put("TokenPush", tokenPush), false) }
    }

    suspend fun mostrarLlamadas(pagina: Int = 1): List<LlamadaReciente> {
        val respuesta = peticion("mostrarLlamadas", JSONObject().put("Pagina", pagina))
        val lista = respuesta.optJSONArray("rrLlamadas") ?: return emptyList()
        return (0 until lista.length()).map { i ->
            val l = lista.getJSONObject(i)
            LlamadaReciente(
                numero = l.optString("Numero"),
                nombre = l.optString("Nombre"),
                direccion = l.optString("Direccion"),
                estatus = l.optString("Estatus"),
                duracion = l.optInt("Duracion"),
                fecha = l.optString("Fecha")
            )
        }
    }
}
