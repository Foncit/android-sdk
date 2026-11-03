package com.foncit.telefono.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Botones de la notificación de llamada */
internal class AccionLlamadaReceiver : BroadcastReceiver() {
    companion object {
        const val RECHAZAR = "com.foncit.telefono.RECHAZAR"
        const val COLGAR = "com.foncit.telefono.COLGAR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RECHAZAR -> MotorLlamadas.rechazar()
            COLGAR -> MotorLlamadas.colgar()
        }
        Notificaciones.quitarEntrante(context)
    }
}
