package com.foncit.telefono.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foncit.telefono.EstadoRegistro
import com.foncit.telefono.EstadoTelefono
import com.foncit.telefono.FoncitTelefono
import com.foncit.telefono.LlamadaReciente
import com.foncit.telefono.R
import com.foncit.telefono.internal.ApiFoncit
import com.foncit.telefono.internal.MotorLlamadas

/**
 * Pantalla completa del teléfono (teclado y recientes). Con esta sola línea la app tiene teléfono:
 * ```
 * FoncitTelefonoPantalla()
 * ```
 * Pide los permisos (micrófono y notificaciones), conecta la extensión y abre la pantalla de llamada cuando hace falta.
 */
@Composable
fun FoncitTelefonoPantalla(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val estado by FoncitTelefono.estado.collectAsState()
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var conPermiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        conPermiso = resultado[Manifest.permission.RECORD_AUDIO] == true || conPermiso
    }

    LaunchedEffect(Unit) {
        val permisos = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
        pedirPermisos.launch(permisos.toTypedArray())
        FoncitTelefono.iniciar()
    }

    TemaFoncit {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = pestana,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = pestana == 0,
                        onClick = { pestana = 0 },
                        text = {
                            Text(
                                stringResource(R.string.foncit_teclado),
                                fontWeight = if (pestana == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = pestana == 1,
                        onClick = { pestana = 1 },
                        text = {
                            Text(
                                stringResource(R.string.foncit_recientes),
                                fontWeight = if (pestana == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Encabezado(estado)

                if (!conPermiso) {
                    AvisoPermiso { pedirPermisos.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                }

                if (estado.llamada != null) {
                    Button(
                        onClick = { MotorLlamadas.abrirPantallaLlamada(contexto) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = VerdeFoncit)
                    ) { Text(stringResource(R.string.foncit_volver_llamada)) }
                }

                val conExtensiones = estado.extensiones.isNotEmpty()
                if (!conExtensiones && estado.registro == EstadoRegistro.ERROR) SinTelefono(estado)

                if (pestana == 0) Teclado(conExtensiones) else Recientes()
            }
        }
    }
}

private fun colorRegistro(registro: EstadoRegistro): Color = when (registro) {
    EstadoRegistro.CONECTADO -> VerdeFoncit
    EstadoRegistro.CONECTANDO -> Color(0xFFF59E0B)
    else -> RojoFoncit
}

/** Extensión con la que se marca y su estado; con varias, al tocarla se elige otra ("Llamar desde"). Todas reciben llamadas */
@Composable
private fun Encabezado(estado: EstadoTelefono) {
    val registro = estado.registro
    val texto = when (registro) {
        EstadoRegistro.CONECTADO -> stringResource(R.string.foncit_conectado)
        EstadoRegistro.CONECTANDO -> stringResource(R.string.foncit_conectando)
        else -> stringResource(R.string.foncit_desconectado)
    }
    val color = colorRegistro(registro)
    var abierto by remember { mutableStateOf(false) }
    val varias = estado.extensiones.size > 1

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = varias) { abierto = true }
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = estado.nombre.ifBlank { stringResource(R.string.foncit_titulo) },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (varias) {
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (varias) {
                                Text(
                                    text = stringResource(R.string.foncit_llamar_desde) + " • ",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (estado.extension.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.foncit_extension, estado.extension),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    estado.extensiones.forEach { ext ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        ext.nombre.ifBlank { stringResource(R.string.foncit_extension, ext.extension) },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.foncit_extension, ext.extension),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colorRegistro(ext.registro))
                                )
                            },
                            trailingIcon = {
                                if (ext.usuario == estado.seleccionada) {
                                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                abierto = false
                                FoncitTelefono.seleccionarExtension(ext.usuario)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.14f),
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = texto,
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (registro == EstadoRegistro.ERROR || registro == EstadoRegistro.DESCONECTADO) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { FoncitTelefono.iniciar() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.foncit_reintentar),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Sin extensiones: por qué y reintentar */
@Composable
private fun SinTelefono(estado: EstadoTelefono) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RojoFoncit.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when {
                estado.sinExtensiones -> stringResource(R.string.foncit_sin_extensiones)
                !estado.mensaje.isNullOrBlank() -> estado.mensaje
                else -> stringResource(R.string.foncit_sin_servidor)
            },
            Modifier.weight(1f), color = RojoFoncit, fontSize = 13.sp
        )
        TextButton(onClick = { FoncitTelefono.iniciar() }) { Text(stringResource(R.string.foncit_reintentar)) }
    }
}

@Composable
private fun AvisoPermiso(alPedir: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RojoFoncit.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.foncit_permiso_microfono), Modifier.weight(1f), color = RojoFoncit, fontSize = 13.sp)
        TextButton(onClick = alPedir) { Text(stringResource(R.string.foncit_permitir)) }
    }
}

private val teclas = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

@Composable
private fun Teclado(puedeLlamar: Boolean) {
    var numero by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = numero.ifBlank { stringResource(R.string.foncit_marcar) },
                fontSize = if (numero.isBlank()) 18.sp else 32.sp,
                color = if (numero.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            teclas.chunked(3).forEach { fila ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    fila.forEach { (digito, letras) ->
                        Tecla(digito, letras, alPresionar = { numero += digito }, alMantener = if (digito == "0") ({ numero += "+" }) else null)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(56.dp))

            IconButton(
                onClick = { if (numero.isNotBlank()) FoncitTelefono.llamar(numero) },
                enabled = puedeLlamar,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (puedeLlamar) VerdeFoncit else VerdeFoncit.copy(alpha = 0.35f))
            ) {
                Icon(
                    Icons.Filled.Call,
                    stringResource(R.string.foncit_llamar),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (numero.isNotEmpty()) {
                    IconButton(
                        onClick = { numero = numero.dropLast(1) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            stringResource(R.string.foncit_borrar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Tecla(digito: String, letras: String, alPresionar: () -> Unit, alMantener: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = alPresionar,
                onLongClick = alMantener
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digito,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letras.isNotBlank()) {
                Text(
                    text = letras,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun Recientes() {
    var llamadas by remember { mutableStateOf<List<LlamadaReciente>?>(null) }
    LaunchedEffect(Unit) {
        llamadas = runCatching { ApiFoncit.mostrarLlamadas() }.getOrDefault(emptyList())
    }
    val lista = llamadas
    if (lista == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        return
    }
    if (lista.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.foncit_sin_recientes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(lista) { l ->
            val perdida = l.direccion == "entrante" && l.estatus != "contestada"
            val (icono, color) = when {
                perdida -> Icons.AutoMirrored.Filled.CallMissed to RojoFoncit
                l.direccion == "saliente" -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
                else -> Icons.AutoMirrored.Filled.CallReceived to VerdeFoncit
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { FoncitTelefono.llamar(l.numero) },
                color = Color.Transparent
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icono, null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = l.nombre.ifBlank { l.numero },
                            color = if (perdida) RojoFoncit else MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = l.fecha.take(16),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { FoncitTelefono.llamar(l.numero) }) {
                        Icon(Icons.Filled.Call, stringResource(R.string.foncit_llamar), tint = VerdeFoncit, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Preview(name = "Pantalla Completa", showBackground = true)
@Composable
fun FoncitTelefonoPantallaPreview() {
    FoncitTelefonoPantalla()
}

@Preview(name = "Pantalla Conectada", showBackground = true)
@Composable
fun FoncitTelefonoPantallaConectadaPreview() {
    TemaFoncit {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = 0,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(selected = true, onClick = {}, text = { Text(stringResource(R.string.foncit_teclado), fontWeight = FontWeight.Bold) })
                    Tab(selected = false, onClick = {}, text = { Text(stringResource(R.string.foncit_recientes)) })
                }
                Encabezado(
                    EstadoTelefono(
                        registro = EstadoRegistro.CONECTADO,
                        extension = "101",
                        nombre = "Soporte Foncit",
                        extensiones = listOf(
                            com.foncit.telefono.ExtensionTelefono("101", "Soporte Foncit", "usr1", EstadoRegistro.CONECTADO)
                        )
                    )
                )
                Teclado(puedeLlamar = true)
            }
        }
    }
}

@Preview(name = "Teclado", showBackground = true)
@Composable
fun TecladoPreview() {
    TemaFoncit {
        Surface(color = MaterialTheme.colorScheme.background) {
            Teclado(puedeLlamar = true)
        }
    }
}

