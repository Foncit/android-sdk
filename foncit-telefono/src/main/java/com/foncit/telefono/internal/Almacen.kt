package com.foncit.telefono.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Datos del teléfono guardados cifrados en el celular: token, datos de la extensión y token de avisos */
internal object Almacen {
    private var preferencias: SharedPreferences? = null

    fun iniciar(context: Context) {
        if (preferencias != null) return
        preferencias = try {
            val llave = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "foncit_telefono", llave,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            /** Si el almacén de llaves falla (algunos equipos viejos) se usan preferencias privadas */
            context.getSharedPreferences("foncit_telefono_respaldo", Context.MODE_PRIVATE)
        }
    }

    var token: String?
        get() = preferencias?.getString("token", null)
        set(valor) { preferencias?.edit()?.putString("token", valor)?.apply() }

    /** Usuario de la plataforma dueño del token (si la app cambia de usuario se borra todo) */
    var usuario: String?
        get() = preferencias?.getString("usuario", null)
        set(valor) { preferencias?.edit()?.putString("usuario", valor)?.apply() }

    /** Ambiente de Foncit (PRODUCCION / PRUEBAS) para conectarse aunque la app esté cerrada */
    var ambiente: String?
        get() = preferencias?.getString("ambiente", null)
        set(valor) { preferencias?.edit()?.putString("ambiente", valor)?.apply() }

    var tokenPush: String?
        get() = preferencias?.getString("token_push", null)
        set(valor) { preferencias?.edit()?.putString("token_push", valor)?.apply() }

    /** De dónde es el token de avisos: android (Firebase) o huawei (Push Kit) */
    var plataformaPush: String?
        get() = preferencias?.getString("plataforma_push", null)
        set(valor) { preferencias?.edit()?.putString("plataforma_push", valor)?.apply() }

    /** Extensiones móviles del usuario (usuario, contraseña SIP, dominio, puerto). Se guardan para conectar sin red al despertar */
    var telefonos: JSONArray?
        get() = preferencias?.getString("telefonos", null)?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?: preferencias?.getString("telefono", null)?.let { runCatching { JSONArray().put(JSONObject(it)) }.getOrNull() }
        set(valor) { preferencias?.edit()?.putString("telefonos", valor?.toString())?.remove("telefono")?.apply() }

    /** Extensión con la que se marca (su usuario SIP) */
    var seleccionada: String?
        get() = preferencias?.getString("seleccionada", null)
        set(valor) { preferencias?.edit()?.putString("seleccionada", valor)?.apply() }

    fun borrarTodo() {
        preferencias?.edit()?.clear()?.apply()
    }
}
