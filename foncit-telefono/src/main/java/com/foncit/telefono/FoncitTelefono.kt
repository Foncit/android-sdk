package com.foncit.telefono

import android.content.Context
import android.util.Log
import com.foncit.telefono.internal.Almacen
import com.foncit.telefono.internal.ApiFoncit
import com.foncit.telefono.internal.MotorLlamadas
import com.foncit.telefono.internal.Notificaciones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foncit: teléfono móvil para cualquier app.
 *
 * Uso:
 * ```
 * // Al abrir la app (o al iniciar sesión en ella): el identificador de su usuario en tu plataforma (ej. AgCuentaUsuario).
 * // La app debe estar en Foncit > Complementos > Plataformas (paquete y huella SHA-256) y el usuario haber llegado de tu panel;
 * // sus extensiones se le asignan desde ese panel.
 * FoncitTelefono.configurar(this, usuario = agCuentaUsuario)
 *
 * // En la pantalla: una línea
 * FoncitTelefonoPantalla()
 *
 * // En tu FirebaseMessagingService (así suena aunque la app esté cerrada; las llaves se suben en Foncit > Complementos > Plataformas)
 * override fun onMessageReceived(m: RemoteMessage) { if (FoncitTelefono.recibirPush(applicationContext, m.data)) return; ... }
 * override fun onNewToken(t: String) { FoncitTelefono.registrarTokenPush(applicationContext, t) }
 *
 * // Celulares Huawei sin Google, en tu HmsMessageService
 * override fun onMessageReceived(m: RemoteMessage) { if (FoncitTelefono.recibirPush(applicationContext, m.dataOfMap)) return; ... }
 * override fun onNewToken(t: String) { FoncitTelefono.registrarTokenPush(applicationContext, t, huawei = true) }
 * ```
 */
object FoncitTelefono {

    internal lateinit var contexto: Context
    /** Ajustes actuales; la pantalla los sigue en vivo (tema y colores) */
    private val _ajustes = MutableStateFlow(AjustesFoncit())
    val ajustesFlujo: StateFlow<AjustesFoncit> = _ajustes.asStateFlow()
    internal val ajustes: AjustesFoncit get() = _ajustes.value
    internal var usuario: String = ""
    internal val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Estado del teléfono (conexión y llamada) para quien quiera pintar su propia interfaz */
    val estado: StateFlow<EstadoTelefono> get() = MotorLlamadas.estado

    /** Si ya se llamó a configurar */
    val configurado: Boolean get() = ::contexto.isInitialized

    /**
     * Avisa cada vez que termina una llamada (entrante o saliente), con su número, dirección, si se contestó y cuánto duró.
     * Sirve para registrarla en tu app, igual que el evento foncit:llamada-finalizada del teléfono web.
     */
    val llamadasFinalizadas: SharedFlow<LlamadaFinalizada> get() = MotorLlamadas.finalizadas

    /**
     * Si el usuario tiene extensiones de Foncit en este celular (las conectadas o las guardadas de la última vez).
     * Con true se puede marcar con llamar(numero); con false la app usa su procedimiento normal.
     */
    fun tieneExtensiones(): Boolean {
        if (!configurado) return false
        if (estado.value.extensiones.isNotEmpty()) return true
        return (Almacen.telefonos?.length() ?: 0) > 0
    }

    /**
     * Configura el teléfono. Llámalo al abrir la app y cada vez que cambie el usuario.
     * @param usuario identificador del usuario en tu plataforma (el mismo que manda tu panel en UsuariosExternos)
     */
    fun configurar(context: Context, usuario: String, ajustes: AjustesFoncit = AjustesFoncit()) {
        contexto = context.applicationContext
        _ajustes.value = ajustes
        Almacen.iniciar(contexto)
        /** Otro usuario en este celular: se olvida el token y las extensiones del anterior */
        if (Almacen.usuario != usuario) {
            if (!Almacen.usuario.isNullOrBlank()) MotorLlamadas.apagar()
            val tokenPush = Almacen.tokenPush
            Almacen.borrarTodo()
            Almacen.tokenPush = tokenPush
            Almacen.usuario = usuario
        }
        this.usuario = usuario
        Almacen.ambiente = ajustes.ambiente.name
        Notificaciones.crearCanales(contexto)
    }

    /** Conecta el teléfono (lo hace solo la pantalla; úsalo si pintas tu propia interfaz) */
    fun iniciar() {
        if (!configurado) return
        alcance.launch { MotorLlamadas.conectar(contexto) }
    }

    /** Con la app cerrada (la abrió un aviso de Firebase) todavía no se llamó a configurar: se usan los datos guardados.
     * Regresa true si hay usuario guardado */
    private fun restaurar(context: Context): Boolean {
        if (!configurado) {
            contexto = context.applicationContext
            Almacen.iniciar(contexto)
            usuario = Almacen.usuario ?: ""
            val ambiente = runCatching { Ambiente.valueOf(Almacen.ambiente ?: "") }.getOrDefault(Ambiente.PRODUCCION)
            _ajustes.value = _ajustes.value.copy(ambiente = ambiente)
            Notificaciones.crearCanales(contexto)
        }
        return usuario.isNotBlank()
    }

    /**
     * Pásale los datos de cada aviso de Firebase (onMessageReceived). Regresa true si era una llamada de Foncit (ya la atendió).
     * Despierta el teléfono para que entre la llamada aunque la app esté cerrada.
     */
    fun recibirPush(context: Context, datos: Map<String, String>): Boolean {
        if (datos["tipo"] != "llamada_foncit") return false
        val conUsuario = restaurar(context)
        Log.i("FoncitTelefono", "Aviso de llamada recibido (usuario guardado: $conUsuario)")
        if (conUsuario) alcance.launch { MotorLlamadas.despertar(contexto) }
        return true
    }

    /** Igual que recibirPush(context, datos) cuando ya se configuró */
    fun recibirPush(datos: Map<String, String>): Boolean {
        if (!configurado) return datos["tipo"] == "llamada_foncit"
        return recibirPush(contexto, datos)
    }

    /**
     * Pásale el token de avisos al abrir la app y cuando cambie (onNewToken). Foncit lo usa para avisar de las llamadas entrantes.
     * @param huawei true si el token es de Push Kit de Huawei (celulares sin Google); si no, es de Firebase
     */
    fun registrarTokenPush(context: Context, token: String, huawei: Boolean = false) {
        if (token.isBlank()) return
        val conUsuario = restaurar(context)
        Almacen.tokenPush = token
        Almacen.plataformaPush = if (huawei) "huawei" else "android"
        Log.i("FoncitTelefono", "Token de avisos recibido (${if (huawei) "huawei" else "android"}, usuario guardado: $conUsuario)")
        if (conUsuario) alcance.launch { ApiFoncit.registrarDispositivo(contexto) }
    }

    /** Igual que registrarTokenPush(context, token) cuando ya se configuró */
    fun registrarTokenPush(token: String, huawei: Boolean = false) {
        if (!configurado) return
        registrarTokenPush(contexto, token, huawei)
    }

    /** Elige con cuál de sus extensiones se marca (su usuario SIP, ExtensionTelefono.usuario). Todas siguen recibiendo llamadas */
    fun seleccionarExtension(usuario: String) {
        if (!configurado) return
        MotorLlamadas.seleccionar(usuario)
    }

    /** Cambia el tema o los colores al momento (ej. cuando la app pasa a modo oscuro o cambia su color) */
    fun cambiarTema(ajustes: AjustesFoncit) {
        _ajustes.value = ajustes
    }

    /** Marca un número */
    fun llamar(numero: String) {
        if (!configurado) return
        alcance.launch { MotorLlamadas.llamar(contexto, numero) }
    }

    /** Al cerrar sesión en tu app: deja de recibir llamadas en este celular y borra los datos del teléfono */
    fun cerrarSesion() {
        if (!configurado) return
        alcance.launch {
            ApiFoncit.quitarDispositivo()
            MotorLlamadas.apagar()
            Almacen.borrarTodo()
        }
    }
}
