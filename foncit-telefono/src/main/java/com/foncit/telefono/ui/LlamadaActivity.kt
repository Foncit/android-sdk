package com.foncit.telefono.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foncit.telefono.EstadoLlamada
import com.foncit.telefono.FoncitTelefono
import com.foncit.telefono.LlamadaActual
import org.linphone.core.R
import com.foncit.telefono.internal.MotorLlamadas
import kotlinx.coroutines.delay

/** Pantalla de llamada (entrante o en curso). Se abre sola y se muestra aun con el celular bloqueado */
class LlamadaActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONTESTAR = "foncit_contestar"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent?.getBooleanExtra(EXTRA_CONTESTAR, false) == true) MotorLlamadas.contestar()

        setContent {
            val estado by FoncitTelefono.estado.collectAsState()
            val llamada = estado.llamada
            LaunchedEffect(llamada == null) {
                if (llamada == null) {
                    delay(700)
                    if (FoncitTelefono.estado.value.llamada == null) finish()
                }
            }
            TemaFoncit { PantallaLlamada(llamada) }
        }
    }
}

@Composable
private fun PantallaLlamada(llamada: LlamadaActual?) {
    var segundos by remember { mutableLongStateOf(0L) }
    var tecladoAbierto by remember { mutableStateOf(false) }
    LaunchedEffect(llamada?.inicioMs) {
        while (llamada?.inicioMs != null) {
            segundos = (System.currentTimeMillis() - llamada.inicioMs) / 1000
            delay(1000)
        }
    }
    val estadoTexto = when (llamada?.estado) {
        EstadoLlamada.ENTRANTE -> stringResource(R.string.foncit_entrante)
        EstadoLlamada.MARCANDO -> stringResource(R.string.foncit_marcando)
        EstadoLlamada.SONANDO -> stringResource(R.string.foncit_sonando)
        EstadoLlamada.EN_ESPERA -> stringResource(R.string.foncit_en_espera)
        EstadoLlamada.EN_CURSO -> "%02d:%02d".format(segundos / 60, segundos % 60)
        else -> stringResource(R.string.foncit_terminada)
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B1326)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF182542)), contentAlignment = Alignment.Center) {
            Text((llamada?.nombre?.ifBlank { null } ?: llamada?.numero ?: "?").take(1).uppercase(), color = Color.White, fontSize = 38.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(llamada?.nombre?.ifBlank { null } ?: llamada?.numero ?: "", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (!llamada?.nombre.isNullOrBlank()) Text(llamada?.numero ?: "", color = Color(0xFF94A3B8), fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(estadoTexto, color = Color(0xFF94A3B8), fontSize = 16.sp)
        Spacer(Modifier.weight(1f))

        if (llamada?.estado == EstadoLlamada.ENTRANTE) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotonRedondo(Icons.Filled.CallEnd, stringResource(R.string.foncit_rechazar), RojoFoncit) { MotorLlamadas.rechazar() }
                BotonRedondo(Icons.Filled.Call, stringResource(R.string.foncit_contestar), VerdeFoncit) { MotorLlamadas.contestar() }
            }
        } else {
            if (tecladoAbierto) {
                TecladoTonos()
                Spacer(Modifier.height(16.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotonOpcion(Icons.Filled.MicOff, stringResource(R.string.foncit_silenciar), llamada?.silenciada == true) { MotorLlamadas.silenciar() }
                BotonOpcion(Icons.Filled.Dialpad, stringResource(R.string.foncit_teclado), tecladoAbierto) { tecladoAbierto = !tecladoAbierto }
                BotonOpcion(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.foncit_altavoz), llamada?.altavoz == true) { MotorLlamadas.altavoz() }
                BotonOpcion(Icons.Filled.Pause, stringResource(R.string.foncit_espera), llamada?.estado == EstadoLlamada.EN_ESPERA) { MotorLlamadas.espera() }
            }
            Spacer(Modifier.height(28.dp))
            BotonRedondo(Icons.Filled.CallEnd, stringResource(R.string.foncit_colgar), RojoFoncit) { MotorLlamadas.colgar() }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TecladoTonos() {
    listOf("123", "456", "789", "*0#").forEach { fila ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            fila.forEach { digito ->
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF182542)).clickable { MotorLlamadas.tono(digito) },
                    contentAlignment = Alignment.Center
                ) { Text(digito.toString(), color = Color.White, fontSize = 22.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BotonRedondo(icono: ImageVector, texto: String, color: Color, alPresionar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClick = alPresionar), contentAlignment = Alignment.Center) {
            Icon(icono, texto, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(texto, color = Color(0xFF94A3B8), fontSize = 13.sp)
    }
}

@Composable
private fun BotonOpcion(icono: ImageVector, texto: String, activo: Boolean, alPresionar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(if (activo) Color.White else Color(0xFF182542)).clickable(onClick = alPresionar),
            contentAlignment = Alignment.Center
        ) { Icon(icono, texto, tint = if (activo) Color(0xFF0B1326) else Color.White) }
        Spacer(Modifier.height(6.dp))
        Text(texto, color = Color(0xFF94A3B8), fontSize = 12.sp)
    }
}
