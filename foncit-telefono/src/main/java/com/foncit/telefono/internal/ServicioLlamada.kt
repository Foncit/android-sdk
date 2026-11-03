package com.foncit.telefono.internal

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Mantiene la llamada viva en segundo plano (servicio de llamada telefónica con su notificación) */
internal class ServicioLlamada : Service() {

    companion object {
        fun iniciar(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ServicioLlamada::class.java)) }
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, ServicioLlamada::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val llamada = MotorLlamadas.estado.value.llamada
        val notificacion = Notificaciones.enCurso(this, llamada?.numero ?: "", llamada?.nombre ?: "")
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        runCatching { ServiceCompat.startForeground(this, Notificaciones.ID_EN_CURSO, notificacion, tipo) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
