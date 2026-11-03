package com.foncit.telefono.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.foncit.telefono.ColoresFoncit
import com.foncit.telefono.FoncitTelefono

/**
 * Colores del teléfono: los de la app (claro / oscuro, AjustesFoncit) y, lo que no mande, los de Foncit.
 * Se lee de FoncitTelefono.ajustesFlujo: si la app cambia de tema o de color, se pinta de nuevo al momento.
 */
@Composable
internal fun TemaFoncit(contenido: @Composable () -> Unit) {
    val ajustes by FoncitTelefono.ajustesFlujo.collectAsState()
    val oscuro = ajustes.oscuro ?: isSystemInDarkTheme()
    val app: ColoresFoncit = if (oscuro) ajustes.coloresOscuro else ajustes.coloresClaro
    val principal = Color(app.principal ?: ajustes.colorPrincipal)
    val sobrePrincipal = if (principal.luminance() > 0.5f) Color(0xFF111827) else Color.White
    val colores = if (oscuro) {
        darkColorScheme(
            primary = principal, onPrimary = sobrePrincipal,
            background = color(app.fondo, 0xFF0B1326), surface = color(app.superficie, 0xFF121C32), surfaceVariant = color(app.superficieVariante, 0xFF182542),
            onBackground = color(app.texto, 0xFFFFFFFF), onSurface = color(app.texto, 0xFFFFFFFF), onSurfaceVariant = color(app.textoSecundario, 0xFF94A3B8)
        )
    } else {
        lightColorScheme(
            primary = principal, onPrimary = sobrePrincipal,
            background = color(app.fondo, 0xFFF8F9FA), surface = color(app.superficie, 0xFFFFFFFF), surfaceVariant = color(app.superficieVariante, 0xFFF3F5F7),
            onBackground = color(app.texto, 0xFF334155), onSurface = color(app.texto, 0xFF334155), onSurfaceVariant = color(app.textoSecundario, 0xFF8996A4)
        )
    }
    MaterialTheme(colorScheme = colores, content = contenido)
}

/** El color de la app o, si no lo mandó, el de Foncit */
private fun color(app: Long?, foncit: Long): Color = Color(app ?: foncit)

internal val VerdeFoncit = Color(0xFF10B981)
internal val RojoFoncit = Color(0xFFEF4444)
