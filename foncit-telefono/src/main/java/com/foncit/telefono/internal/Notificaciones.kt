package com.foncit.telefono.internal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import org.linphone.core.R
import com.foncit.telefono.ui.LlamadaActivity

/** Notificaciones de llamada: entrante (pantalla completa, aun con el celular bloqueado) y en curso */
internal object Notificaciones {
    const val CANAL_ENTRANTES = "foncit_llamadas_entrantes"
    const val CANAL_EN_CURSO = "foncit_llamada_en_curso"
    const val ID_ENTRANTE = 7101
    const val ID_EN_CURSO = 7102

    fun crearCanales(context: Context) {
        val gestor = NotificationManagerCompat.from(context)
        val entrantes = NotificationChannelCompat.Builder(CANAL_ENTRANTES, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.foncit_canal_entrantes))
            .setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setVibrationEnabled(true)
            .build()
        val enCurso = NotificationChannelCompat.Builder(CANAL_EN_CURSO, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.foncit_canal_en_curso))
            .build()
        gestor.createNotificationChannel(entrantes)
        gestor.createNotificationChannel(enCurso)
    }

    private fun accion(context: Context, accion: String, codigo: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, codigo,
            Intent(context, AccionLlamadaReceiver::class.java).setAction(accion),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pantalla(context: Context, contestar: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context, if (contestar) 2 else 1,
            Intent(context, LlamadaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(LlamadaActivity.EXTRA_CONTESTAR, contestar),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun mostrarEntrante(context: Context, numero: String, nombre: String) {
        val persona = Person.Builder().setName(nombre.ifBlank { numero.ifBlank { context.getString(R.string.foncit_desconocido) } }).setImportant(true).build()
        val notificacion = NotificationCompat.Builder(context, CANAL_ENTRANTES)
            .setSmallIcon(R.drawable.foncit_ic_llamada)
            .setContentTitle(context.getString(R.string.foncit_entrante))
            .setContentText(numero)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(pantalla(context, false), true)
            .setContentIntent(pantalla(context, false))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(persona, accion(context, AccionLlamadaReceiver.RECHAZAR, 11), pantalla(context, true)))
            .build()
        val gestor = NotificationManagerCompat.from(context)
        if (gestor.areNotificationsEnabled()) runCatching { gestor.notify(ID_ENTRANTE, notificacion) }
    }

    fun quitarEntrante(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ENTRANTE)
    }

    /** La del servicio mientras hay una llamada en curso */
    fun enCurso(context: Context, numero: String, nombre: String): android.app.Notification {
        val persona = Person.Builder().setName(nombre.ifBlank { numero.ifBlank { context.getString(R.string.foncit_desconocido) } }).build()
        return NotificationCompat.Builder(context, CANAL_EN_CURSO)
            .setSmallIcon(R.drawable.foncit_ic_llamada)
            .setContentTitle(context.getString(R.string.foncit_llamada_en_curso))
            .setContentText(numero)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(pantalla(context, false))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(persona, accion(context, AccionLlamadaReceiver.COLGAR, 12)))
            .build()
    }
}
