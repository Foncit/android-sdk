package com.foncit.telefono

/** Ambiente de Foncit al que se conecta el teléfono (las rutas están en internal/Rutas.kt) */
enum class Ambiente { PRODUCCION, PRUEBAS }

/**
 * Colores de la app para un modo (claro u oscuro), en ARGB (ej. 0xFF0B1326). Los que no se manden quedan los de Foncit.
 * @param principal botones y acentos
 * @param fondo fondo de la pantalla
 * @param superficie tarjetas y barras
 * @param superficieVariante teclas del teclado y chips
 * @param texto texto principal
 * @param textoSecundario textos de ayuda
 */
data class ColoresFoncit(
    val principal: Long? = null,
    val fondo: Long? = null,
    val superficie: Long? = null,
    val superficieVariante: Long? = null,
    val texto: Long? = null,
    val textoSecundario: Long? = null
)

/**
 * Ajustes del teléfono. Todos son opcionales.
 * @param ambiente PRODUCCION (webservices.foncit.com) o PRUEBAS (stg-webservices.foncit.com)
 * @param colorPrincipal color de los botones (ARGB) si los colores del modo no traen principal; por defecto el naranja de Foncit
 * @param oscuro tema oscuro; null = el del sistema
 * @param coloresClaro colores de la app en modo claro (los que falten, los de Foncit)
 * @param coloresOscuro colores de la app en modo oscuro (los que falten, los de Foncit)
 */
data class AjustesFoncit(
    val ambiente: Ambiente = Ambiente.PRODUCCION,
    val colorPrincipal: Long = 0xFFE9791E,
    val oscuro: Boolean? = null,
    val coloresClaro: ColoresFoncit = ColoresFoncit(),
    val coloresOscuro: ColoresFoncit = ColoresFoncit()
)

enum class EstadoRegistro { DESCONECTADO, CONECTANDO, CONECTADO, ERROR }

enum class EstadoLlamada { ENTRANTE, MARCANDO, SONANDO, EN_CURSO, EN_ESPERA, TERMINADA }

/** Llamada que está sonando o en curso */
data class LlamadaActual(
    val numero: String,
    val nombre: String,
    val entrante: Boolean,
    val estado: EstadoLlamada,
    val inicioMs: Long? = null,
    val silenciada: Boolean = false,
    val altavoz: Boolean = false
)

/** Una extensión móvil del usuario (todas reciben llamadas; se marca desde la seleccionada) */
data class ExtensionTelefono(
    val usuario: String,
    val extension: String,
    val nombre: String,
    val registro: EstadoRegistro = EstadoRegistro.CONECTANDO
)

/**
 * Estado del teléfono que pinta la interfaz.
 * registro, extension y nombre son los de la extensión seleccionada (con la que se marca).
 */
data class EstadoTelefono(
    val registro: EstadoRegistro = EstadoRegistro.DESCONECTADO,
    val extension: String = "",
    val nombre: String = "",
    val extensiones: List<ExtensionTelefono> = emptyList(),
    val seleccionada: String = "",
    val llamada: LlamadaActual? = null,
    val mensaje: String? = null,
    /** Foncit respondió que al usuario aún no le asignan extensiones (desde el panel de su plataforma) */
    val sinExtensiones: Boolean = false
)

/** Una llamada del historial de Foncit */
data class LlamadaReciente(
    val numero: String,
    val nombre: String,
    val direccion: String,
    val estatus: String,
    val duracion: Int,
    val fecha: String
)
