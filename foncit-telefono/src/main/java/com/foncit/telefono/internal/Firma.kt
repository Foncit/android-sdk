package com.foncit.telefono.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Llaves con que está firmada la app: SHA-1 y SHA-256 de cada certificado de firma (las mismas de keytool, Play Console o AppGallery).
 * Foncit deja entrar a la app si alguna está registrada en Complementos > Plataformas, esté o no en la tienda.
 */
internal object Firma {

    fun llaves(context: Context): String = runCatching {
        val gestor = context.packageManager
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = gestor.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            when {
                info == null -> emptyArray()
                info.hasMultipleSigners() -> info.apkContentsSigners
                else -> info.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            gestor.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: emptyArray()
        firmas.flatMap { firma ->
            listOf("SHA-1", "SHA-256").map { algoritmo ->
                MessageDigest.getInstance(algoritmo).digest(firma.toByteArray()).joinToString("") { "%02X".format(it) }
            }
        }.distinct().joinToString(",")
    }.getOrDefault("")
}
