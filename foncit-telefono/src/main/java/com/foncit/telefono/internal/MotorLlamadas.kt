package com.foncit.telefono.internal

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.os.Looper
import com.foncit.telefono.EstadoLlamada
import com.foncit.telefono.EstadoRegistro
import com.foncit.telefono.EstadoTelefono
import com.foncit.telefono.ExtensionTelefono
import com.foncit.telefono.LlamadaActual
import com.foncit.telefono.ui.LlamadaActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * Motor de llamadas (Linphone): una cuenta SIP por cada extensión móvil del usuario, por TLS y con voz cifrada (SRTP) si la extensión la tiene.
 * Todas reciben llamadas; se marca desde la seleccionada. Las extensiones salen de Foncit cada vez que conecta (ApiFoncit.mostrarTelefonos):
 * las que ya no existen se quitan y las nuevas se agregan. Se guardan para conectar rápido al despertar por un aviso.
 */
internal object MotorLlamadas {

    private val _estado = MutableStateFlow(EstadoTelefono())
    val estado: StateFlow<EstadoTelefono> = _estado.asStateFlow()

    private var core: Core? = null
    private val principal = Handler(Looper.getMainLooper())

    /** Extensiones actuales (los datos de Foncit) y el estado de registro de cada una por su usuario SIP */
    private var telefonos: List<JSONObject> = emptyList()
    private val registros = mutableMapOf<String, EstadoRegistro>()
    private var mensajeRegistro: String? = null

    private val escucha = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String) {
            val registro = when (state) {
                RegistrationState.Ok -> EstadoRegistro.CONECTADO
                RegistrationState.Progress, RegistrationState.Refreshing -> EstadoRegistro.CONECTANDO
                RegistrationState.Failed -> EstadoRegistro.ERROR
                else -> EstadoRegistro.DESCONECTADO
            }
            val usuario = account.params.identityAddress?.username ?: return
            /** Logcat, filtro FoncitTelefono: así se ve por qué una extensión no conecta (sin contraseñas) */
            Log.i("FoncitTelefono", "Registro de la extensión $usuario en ${account.params.serverAddress?.asStringUriOnly()}: $state $message")
            registros[usuario] = registro
            if (registro == EstadoRegistro.ERROR) mensajeRegistro = message
            publicarExtensiones()
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            val contexto = com.foncit.telefono.FoncitTelefono.contexto
            when (state) {
                Call.State.IncomingReceived -> {
                    actualizarLlamada(call, EstadoLlamada.ENTRANTE)
                    Notificaciones.mostrarEntrante(contexto, numeroDe(call), nombreDe(call))
                    /** Con la app abierta se muestra la pantalla de llamada de una vez (en segundo plano la abre la notificación) */
                    runCatching { abrirPantallaLlamada(contexto) }
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> actualizarLlamada(call, EstadoLlamada.MARCANDO)
                Call.State.OutgoingRinging -> actualizarLlamada(call, EstadoLlamada.SONANDO)
                Call.State.Connected, Call.State.StreamsRunning, Call.State.Resuming -> {
                    Notificaciones.quitarEntrante(contexto)
                    actualizarLlamada(call, EstadoLlamada.EN_CURSO)
                    ServicioLlamada.iniciar(contexto)
                }
                Call.State.Paused, Call.State.PausedByRemote -> actualizarLlamada(call, EstadoLlamada.EN_ESPERA)
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    if (core.callsNb == 0) {
                        Notificaciones.quitarEntrante(contexto)
                        ServicioLlamada.detener(contexto)
                        _estado.update { it.copy(llamada = null) }
                    }
                }
                else -> {}
            }
        }
    }

    private fun numeroDe(call: Call): String = call.remoteAddress.username ?: ""
    private fun nombreDe(call: Call): String = call.remoteAddress.displayName ?: ""

    private fun actualizarLlamada(call: Call, estadoLlamada: EstadoLlamada) {
        val anterior = _estado.value.llamada
        val inicio = if (estadoLlamada == EstadoLlamada.EN_CURSO || estadoLlamada == EstadoLlamada.EN_ESPERA) {
            anterior?.inicioMs ?: (System.currentTimeMillis() - call.duration * 1000L)
        } else null
        _estado.update {
            it.copy(llamada = LlamadaActual(
                numero = numeroDe(call),
                nombre = nombreDe(call),
                entrante = call.dir == Call.Dir.Incoming,
                estado = estadoLlamada,
                inicioMs = inicio,
                silenciada = call.microphoneMuted,
                altavoz = call.outputAudioDevice?.type == AudioDevice.Type.Speaker
            ))
        }
    }

    /** Crea el motor una sola vez (TLS, SRTP obligatorio, timbre del sistema) */
    private fun asegurarCore(context: Context): Core {
        core?.let { return it }
        val fabrica = Factory.instance()
        fabrica.setDebugMode(false, "FoncitTelefono")
        val nuevo = fabrica.createCore(null, null, context)
        nuevo.isPushNotificationEnabled = false
        /** Voz cifrada (SRTP) cuando la extensión la tiene (media_encryption = sdes); si no, la llamada entra igual sin cifrar */
        nuevo.mediaEncryption = MediaEncryption.SRTP
        nuevo.isMediaEncryptionMandatory = false
        nuevo.isNativeRingingEnabled = true
        nuevo.verifyServerCertificates(true)
        nuevo.verifyServerCn(true)
        val transportes = nuevo.transports
        transportes.udpPort = 0
        transportes.tcpPort = 0
        transportes.tlsPort = -1
        nuevo.transports = transportes
        nuevo.addListener(escucha)
        nuevo.start()
        core = nuevo
        return nuevo
    }

    private fun usuarioDe(telefono: JSONObject) = telefono.optString("Usuario")
    private fun identidadDe(telefono: JSONObject) = "sip:${telefono.optString("Usuario")}@${telefono.optString("Dominio")}"
    private fun cuentaDe(usuario: String): Account? = core?.accountList?.firstOrNull { it.params.identityAddress?.username == usuario }

    /** La extensión con la que se marca: la elegida o, si ya no está, la primera */
    private fun telefonoSeleccionado(): JSONObject? =
        telefonos.firstOrNull { usuarioDe(it) == Almacen.seleccionada } ?: telefonos.firstOrNull()

    /** Pasa a la interfaz las extensiones con su estado; registro, extensión y nombre son los de la seleccionada */
    private fun publicarExtensiones() {
        val seleccionado = telefonoSeleccionado()
        val usuarioSeleccionado = seleccionado?.let { usuarioDe(it) } ?: ""
        val lista = telefonos.map {
            ExtensionTelefono(
                usuario = usuarioDe(it),
                extension = it.optString("Extension"),
                nombre = it.optString("Nombre"),
                registro = registros[usuarioDe(it)] ?: EstadoRegistro.CONECTANDO
            )
        }
        val registro = when {
            seleccionado == null -> EstadoRegistro.DESCONECTADO
            else -> registros[usuarioSeleccionado] ?: EstadoRegistro.CONECTANDO
        }
        _estado.update {
            it.copy(
                extensiones = lista,
                seleccionada = usuarioSeleccionado,
                extension = seleccionado?.optString("Extension") ?: "",
                nombre = seleccionado?.optString("Nombre") ?: "",
                registro = registro,
                mensaje = if (registro == EstadoRegistro.ERROR) mensajeRegistro else it.mensaje
            )
        }
    }

    /** Deja en Linphone exactamente las extensiones de la lista: quita las que ya no están y agrega las nuevas (cada una con su contraseña) */
    private fun configurarCuentas(core: Core, lista: JSONArray) {
        val nuevos = (0 until lista.length()).map { lista.getJSONObject(it) }
            .filter { usuarioDe(it).isNotBlank() && it.optString("Dominio").isNotBlank() }
        val identidades = nuevos.map { identidadDe(it) }.toSet()

        core.accountList.filter { it.params.identityAddress?.asStringUriOnly() !in identidades }.forEach { cuenta ->
            val usuario = cuenta.params.identityAddress?.username
            val dominio = cuenta.params.identityAddress?.domain
            core.removeAccount(cuenta)
            core.findAuthInfo(null, usuario, dominio)?.let { core.removeAuthInfo(it) }
            if (usuario != null) registros.remove(usuario)
        }

        val fabrica = Factory.instance()
        val actuales = core.accountList.mapNotNull { it.params.identityAddress?.asStringUriOnly() }.toSet()
        nuevos.filter { identidadDe(it) !in actuales }.forEach { telefono ->
            val usuario = usuarioDe(telefono)
            val dominio = telefono.optString("Dominio")
            val puerto = telefono.optString("Puerto", "5061")
            core.addAuthInfo(fabrica.createAuthInfo(usuario, null, telefono.optString("Password"), null, null, dominio, null))
            val parametros = core.createAccountParams()
            parametros.identityAddress = fabrica.createAddress(identidadDe(telefono))
            val servidor = fabrica.createAddress("sip:$dominio:$puerto")
            servidor?.transport = TransportType.Tls
            parametros.serverAddress = servidor
            parametros.isRegisterEnabled = true
            core.addAccount(core.createAccount(parametros))
            registros[usuario] = EstadoRegistro.CONECTANDO
        }

        telefonos = nuevos
        telefonoSeleccionado()?.let { seleccionado -> cuentaDe(usuarioDe(seleccionado))?.let { core.defaultAccount = it } }
        publicarExtensiones()
    }

    /** Elige con cuál extensión se marca */
    fun seleccionar(usuario: String) {
        principal.post {
            if (telefonos.none { usuarioDe(it) == usuario }) return@post
            Almacen.seleccionada = usuario
            cuentaDe(usuario)?.let { core?.defaultAccount = it }
            publicarExtensiones()
        }
    }

    /** Pide a Foncit sus extensiones actuales (si hay red) y las conecta todas */
    suspend fun conectar(context: Context) {
        val core = asegurarCore(context)
        _estado.update { it.copy(registro = if (it.registro == EstadoRegistro.CONECTADO) it.registro else EstadoRegistro.CONECTANDO, mensaje = null) }
        val lista = try {
            ApiFoncit.mostrarTelefonos() ?: Almacen.telefonos
        } catch (e: TokenInvalido) {
            /** Foncit ya no reconoce al usuario: se quitan sus extensiones del celular */
            configurarCuentas(core, JSONArray())
            _estado.update { it.copy(registro = EstadoRegistro.ERROR, mensaje = e.message, sinExtensiones = false) }
            return
        } catch (e: Exception) {
            Almacen.telefonos
        }
        if (lista == null || lista.length() == 0) {
            configurarCuentas(core, JSONArray())
            _estado.update { it.copy(registro = EstadoRegistro.ERROR, sinExtensiones = lista != null) }
            return
        }
        _estado.update { it.copy(sinExtensiones = false) }
        configurarCuentas(core, lista)
        core.refreshRegisters()
        ApiFoncit.registrarDispositivo(context)
    }

    /** Llegó un aviso de llamada: se conecta ya con los datos guardados para que la llamada entre */
    suspend fun despertar(context: Context) {
        val core = asegurarCore(context)
        val guardados = Almacen.telefonos
        if (guardados != null && guardados.length() > 0) {
            configurarCuentas(core, guardados)
            core.refreshRegisters()
        } else {
            conectar(context)
        }
    }

    suspend fun llamar(context: Context, numero: String) {
        val limpio = numero.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (limpio.isBlank()) return
        val core = asegurarCore(context)
        if (telefonos.isEmpty()) conectar(context)
        /** Desde la extensión seleccionada */
        val telefono = telefonoSeleccionado() ?: return
        val destino = Factory.instance().createAddress("sip:$limpio@${telefono.optString("Dominio")}") ?: return
        val parametros = core.createCallParams(null) ?: return
        parametros.mediaEncryption = MediaEncryption.SRTP
        cuentaDe(usuarioDe(telefono))?.let { parametros.account = it }
        core.inviteAddressWithParams(destino, parametros)
        abrirPantallaLlamada(context)
    }

    fun abrirPantallaLlamada(context: Context) {
        val intento = Intent(context, LlamadaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intento)
    }

    private fun llamadaActual(): Call? = core?.currentCall ?: core?.calls?.firstOrNull()

    fun contestar() {
        val call = llamadaActual() ?: return
        /** Con el cifrado que trae la llamada (SRTP si la extensión lo tiene) */
        val parametros = core?.createCallParams(call)
        if (parametros != null) call.acceptWithParams(parametros) else call.accept()
    }

    fun rechazar() {
        llamadaActual()?.decline(Reason.Declined)
    }

    fun colgar() {
        val call = llamadaActual() ?: return
        if (call.state == Call.State.IncomingReceived) call.decline(Reason.Declined) else call.terminate()
    }

    fun silenciar() {
        val call = llamadaActual() ?: return
        call.microphoneMuted = !call.microphoneMuted
        _estado.update { it.copy(llamada = it.llamada?.copy(silenciada = call.microphoneMuted)) }
    }

    fun altavoz() {
        val call = llamadaActual() ?: return
        val actual = call.outputAudioDevice?.type == AudioDevice.Type.Speaker
        val buscado = if (actual) AudioDevice.Type.Earpiece else AudioDevice.Type.Speaker
        val dispositivo = core?.audioDevices?.firstOrNull { it.type == buscado } ?: return
        call.outputAudioDevice = dispositivo
        _estado.update { it.copy(llamada = it.llamada?.copy(altavoz = !actual)) }
    }

    fun espera() {
        val call = llamadaActual() ?: return
        if (call.state == Call.State.Paused) call.resume() else call.pause()
    }

    fun tono(digito: Char) {
        llamadaActual()?.sendDtmf(digito)
    }

    fun apagar() {
        principal.post {
            core?.removeListener(escucha)
            core?.stop()
            core = null
            telefonos = emptyList()
            registros.clear()
            mensajeRegistro = null
            _estado.value = EstadoTelefono()
        }
    }
}
